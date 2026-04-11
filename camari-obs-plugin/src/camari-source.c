#include "camari-source.h"
#include "mjpeg-parser.h"
#include "reconnect.h"
#include "mdns-discovery.h"

#include <obs-module.h>
#include <util/platform.h>
#include <util/threading.h>

#include <curl/curl.h>
#include <turbojpeg.h>

#include <stdlib.h>
#include <string.h>
#include <stdio.h>

/* ── Constants ─────────────────────────────────────────────────────────────── */

#define PROP_IP            "ip_address"
#define PROP_PORT          "port"
#define PROP_AUTO_RECONNECT "auto_reconnect"
#define PROP_TEST_CONN     "test_connection"
#define PROP_STATUS        "status_text"
#define PROP_MDNS_HINT     "mdns_hint"
#define PROP_DEVICE_LIST   "device_list"
#define PROP_DISCOVER      "discover_button"

#define DEFAULT_PORT        8080
#define CURL_CONNECT_TIMEOUT_S 5L
#define CURL_LOW_SPEED_LIMIT   1L    /* bytes/s */
#define CURL_LOW_SPEED_TIME    10L   /* seconds */

/* ── Frame callback context ─────────────────────────────────────────────────── */

typedef struct {
    CamariSourceInstance *inst;
    tjhandle              tj;
    MjpegParser           parser;
} StreamCtx;

/* ── Forward declarations ───────────────────────────────────────────────────── */

static void  *stream_thread(void *arg);
static void   start_stream_thread(CamariSourceInstance *inst);
static void   stop_stream_thread(CamariSourceInstance *inst);

/* ── JPEG decode + OBS frame push ───────────────────────────────────────────── */

static void on_jpeg_frame(const uint8_t *jpeg, size_t len, void *userdata)
{
    StreamCtx            *ctx  = userdata;
    CamariSourceInstance *inst = ctx->inst;

    int width = 0, height = 0, subsamp = 0, colorspace = 0;
    if (tjDecompressHeader3(ctx->tj, jpeg, (unsigned long)len,
                            &width, &height, &subsamp, &colorspace) != 0)
        return;

    if (width <= 0 || height <= 0) return;

    size_t needed = (size_t)width * (size_t)height * 4; /* RGBA */

    pthread_mutex_lock(&inst->frame_mutex);

    /* Reallocate buffer if resolution changed */
    if (needed != inst->frame_buffer_size) {
        free(inst->frame_buffer);
        inst->frame_buffer      = malloc(needed);
        inst->frame_buffer_size = needed;
        inst->frame_width       = (uint32_t)width;
        inst->frame_height      = (uint32_t)height;
    }

    uint8_t *buf = inst->frame_buffer;
    if (!buf) {
        pthread_mutex_unlock(&inst->frame_mutex);
        return;
    }

    if (tjDecompress2(ctx->tj, jpeg, (unsigned long)len,
                      buf, width, 0, height, TJPF_RGBA, TJFLAG_FASTDCT) != 0) {
        pthread_mutex_unlock(&inst->frame_mutex);
        return;
    }

    struct obs_source_frame frame = {
        .data[0]    = buf,
        .linesize[0] = (uint32_t)(width * 4),
        .width      = (uint32_t)width,
        .height     = (uint32_t)height,
        .timestamp  = os_gettime_ns(),
        .format     = VIDEO_FORMAT_RGBA,
    };

    inst->state = CAMARI_STATE_CONNECTED;
    obs_source_output_video(inst->source, &frame);

    pthread_mutex_unlock(&inst->frame_mutex);
}

/* ── libcurl write callback ──────────────────────────────────────────────────── */

static size_t curl_write_cb(char *ptr, size_t size, size_t nmemb, void *userdata)
{
    StreamCtx *ctx = userdata;
    size_t total   = size * nmemb;

    if (ctx->inst->thread_stop) return 0; /* abort transfer */

    mjpeg_parser_feed(&ctx->parser, (const uint8_t *)ptr, total);
    return total;
}

/* ── Stream thread ──────────────────────────────────────────────────────────── */

static void *stream_thread(void *arg)
{
    CamariSourceInstance *inst = arg;
    ReconnectState        reconnect = {0};

    StreamCtx ctx = {0};
    ctx.inst = inst;
    ctx.tj   = tjInitDecompress();
    mjpeg_parser_init(&ctx.parser, on_jpeg_frame, &ctx);

    while (!inst->thread_stop) {
        inst->state = CAMARI_STATE_CONNECTING;

        char url[512];
        snprintf(url, sizeof(url), "http://%s:%d/stream",
                 inst->config.ip_address, inst->config.port);

        CURL *curl = curl_easy_init();
        if (curl) {
            curl_easy_setopt(curl, CURLOPT_URL,            url);
            curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION,  curl_write_cb);
            curl_easy_setopt(curl, CURLOPT_WRITEDATA,      &ctx);
            curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, CURL_CONNECT_TIMEOUT_S);
            curl_easy_setopt(curl, CURLOPT_LOW_SPEED_LIMIT,CURL_LOW_SPEED_LIMIT);
            curl_easy_setopt(curl, CURLOPT_LOW_SPEED_TIME, CURL_LOW_SPEED_TIME);
            curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);

            curl_easy_perform(curl); /* blocks until stream ends or thread_stop */
            curl_easy_cleanup(curl);
        }

        inst->state = CAMARI_STATE_DISCONNECTED;
        mjpeg_parser_reset(&ctx.parser);

        /* Output a black frame so OBS doesn't freeze on last image */
        pthread_mutex_lock(&inst->frame_mutex);
        if (inst->frame_buffer && inst->frame_width && inst->frame_height) {
            memset(inst->frame_buffer, 0, inst->frame_buffer_size);
            struct obs_source_frame black = {
                .data[0]    = inst->frame_buffer,
                .linesize[0] = inst->frame_width * 4,
                .width      = inst->frame_width,
                .height     = inst->frame_height,
                .timestamp  = os_gettime_ns(),
                .format     = VIDEO_FORMAT_RGBA,
            };
            obs_source_output_video(inst->source, &black);
        }
        pthread_mutex_unlock(&inst->frame_mutex);

        if (inst->thread_stop) break;
        if (!inst->config.auto_reconnect) break;

        /* Wait 2 s before retrying */
        reconnect_schedule(&reconnect, os_gettime_ns());
        while (!inst->thread_stop) {
            if (reconnect_due(&reconnect, os_gettime_ns())) {
                reconnect_cancel(&reconnect);
                break;
            }
            os_sleep_ms(100);
        }
    }

    mjpeg_parser_destroy(&ctx.parser);
    if (ctx.tj) tjDestroy(ctx.tj);

    inst->thread_running = false;
    return NULL;
}

static void start_stream_thread(CamariSourceInstance *inst)
{
    if (inst->thread_running) return;
    if (inst->config.ip_address[0] == '\0') return;

    inst->thread_stop    = false;
    inst->thread_running = true;
    pthread_create(&inst->stream_thread, NULL, stream_thread, inst);
}

static void stop_stream_thread(CamariSourceInstance *inst)
{
    if (!inst->thread_running) return;
    inst->thread_stop = true;
    pthread_join(inst->stream_thread, NULL);
    inst->thread_running = false;
}

/* ── OBS source callbacks ───────────────────────────────────────────────────── */

static const char *camari_get_name(void *type_data)
{
    (void)type_data;
    return "Camari";
}

static void *camari_create(obs_data_t *settings, obs_source_t *source)
{
    CamariSourceInstance *inst = bzalloc(sizeof(*inst));
    inst->source = source;
    inst->state  = CAMARI_STATE_DISCONNECTED;
    pthread_mutex_init(&inst->frame_mutex, NULL);

    const char *ip = obs_data_get_string(settings, PROP_IP);
    snprintf(inst->config.ip_address, sizeof(inst->config.ip_address),
             "%s", ip ? ip : "");
    inst->config.port           = (int)obs_data_get_int(settings, PROP_PORT);
    inst->config.auto_reconnect = obs_data_get_bool(settings, PROP_AUTO_RECONNECT);

    if (inst->config.port == 0) inst->config.port = DEFAULT_PORT;

    start_stream_thread(inst);
    return inst;
}

static void camari_destroy(void *data)
{
    CamariSourceInstance *inst = data;
    stop_stream_thread(inst);

    pthread_mutex_destroy(&inst->frame_mutex);
    free(inst->frame_buffer);
    bfree(inst);
}

static uint32_t camari_get_width(void *data)
{
    return ((CamariSourceInstance *)data)->frame_width;
}

static uint32_t camari_get_height(void *data)
{
    return ((CamariSourceInstance *)data)->frame_height;
}

static void camari_video_tick(void *data, float seconds)
{
    (void)seconds;
    CamariSourceInstance *inst = data;

    /* Restart thread if it exited unexpectedly and auto_reconnect is on */
    if (!inst->thread_running && inst->config.auto_reconnect && !inst->thread_stop)
        start_stream_thread(inst);
}

/* Fires when user picks an item from the discovered-devices dropdown.
   Parses the "ip:port" value and writes it into the IP and port settings. */
static bool camari_device_selected(obs_properties_t *props,
                                   obs_property_t   *prop,
                                   obs_data_t       *settings)
{
    (void)props;
    (void)prop;
    const char *val = obs_data_get_string(settings, PROP_DEVICE_LIST);
    if (!val || val[0] == '\0') return false;

    /* val is "ip:port" e.g. "192.168.1.42:8080" */
    char ip[64] = {0};
    int  port   = 0;
    if (sscanf(val, "%63[^:]:%d", ip, &port) == 2 && ip[0] != '\0' && port > 0) {
        obs_data_set_string(settings, PROP_IP,   ip);
        obs_data_set_int   (settings, PROP_PORT, (long long)port);
    }
    return true;
}

/* Fires when user clicks "Discover Devices".
   Runs a blocking 2-second mDNS scan and repopulates the dropdown. */
static bool camari_discover_clicked(obs_properties_t *props,
                                    obs_property_t   *prop,
                                    void             *data)
{
    (void)prop;
    (void)data;

    CamariDiscoveryResult result;
    camari_discover(2000, &result);

    obs_property_t *list = obs_properties_get(props, PROP_DEVICE_LIST);
    obs_property_list_clear(list);

    if (result.count == 0) {
        obs_property_list_add_string(list, "No Camari devices found", "");
    } else {
        for (int i = 0; i < result.count; i++) {
            char label[384];
            char value[96];
            snprintf(label, sizeof(label), "%s (%s:%d)",
                     result.devices[i].name,
                     result.devices[i].address,
                     result.devices[i].port);
            snprintf(value, sizeof(value), "%s:%d",
                     result.devices[i].address,
                     result.devices[i].port);
            obs_property_list_add_string(list, label, value);
        }
    }

    return true; /* tells OBS to re-render properties */
}

static obs_properties_t *camari_get_properties(void *data)
{
    (void)data;
    obs_properties_t *props = obs_properties_create();

    obs_properties_add_text(props, PROP_IP,
                            "Phone IP or hostname", OBS_TEXT_DEFAULT);

    obs_properties_add_text(props, PROP_MDNS_HINT,
                            "Tip: type camari.local to use mDNS auto-discovery",
                            OBS_TEXT_INFO);

    obs_property_t *port_prop = obs_properties_add_int(
        props, PROP_PORT, "Port", 1024, 65535, 1);
    obs_property_int_set_suffix(port_prop, "");

    obs_properties_add_bool(props, PROP_AUTO_RECONNECT, "Auto-reconnect");

    /* Discovery dropdown + button */
    obs_property_t *list = obs_properties_add_list(
        props, PROP_DEVICE_LIST,
        "Found devices",
        OBS_COMBO_TYPE_LIST,
        OBS_COMBO_FORMAT_STRING);
    obs_property_list_add_string(list, "(click Discover first)", "");
    obs_property_set_modified_callback(list, camari_device_selected);

    obs_properties_add_button(props, PROP_DISCOVER,
                              "Discover Devices", camari_discover_clicked);

    obs_properties_add_button(props, PROP_TEST_CONN, "Test Connection",
                              camari_test_connection);

    obs_properties_add_text(props, PROP_STATUS,
                            "Status", OBS_TEXT_INFO);

    return props;
}

static bool camari_test_connection(obs_properties_t *props,
                                   obs_property_t *prop,
                                   void *data)
{
    (void)props;
    (void)prop;
    CamariSourceInstance *inst = data;

    char url[512];
    snprintf(url, sizeof(url), "http://%s:%d/health",
             inst->config.ip_address, inst->config.port);

    CURL *curl = curl_easy_init();
    long  http_code = 0;
    bool  ok        = false;

    if (curl) {
        curl_easy_setopt(curl, CURLOPT_URL,            url);
        curl_easy_setopt(curl, CURLOPT_NOBODY,         1L);
        curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, 3L);
        CURLcode res = curl_easy_perform(curl);
        if (res == CURLE_OK)
            curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);
        curl_easy_cleanup(curl);
        ok = (res == CURLE_OK && http_code == 200);
    }

    obs_data_t *settings = obs_source_get_settings(inst->source);
    obs_data_set_string(settings, PROP_STATUS,
                        ok ? "Connected" : "Could not reach Camari");
    obs_data_release(settings);
    obs_source_update(inst->source, NULL);

    return true;
}

static void camari_update(void *data, obs_data_t *settings)
{
    CamariSourceInstance *inst = data;

    const char *ip = obs_data_get_string(settings, PROP_IP);
    int         port = (int)obs_data_get_int(settings, PROP_PORT);
    bool        auto_reconnect = obs_data_get_bool(settings, PROP_AUTO_RECONNECT);

    if (port == 0) port = DEFAULT_PORT;

    bool changed = (strncmp(inst->config.ip_address, ip ? ip : "",
                            sizeof(inst->config.ip_address)) != 0)
                || (inst->config.port != port);

    snprintf(inst->config.ip_address, sizeof(inst->config.ip_address),
             "%s", ip ? ip : "");
    inst->config.port           = port;
    inst->config.auto_reconnect = auto_reconnect;

    if (changed) {
        stop_stream_thread(inst);
        start_stream_thread(inst);
    }
}

static void camari_get_defaults(obs_data_t *settings)
{
    obs_data_set_default_string(settings, PROP_IP,             "");
    obs_data_set_default_int(   settings, PROP_PORT,           DEFAULT_PORT);
    obs_data_set_default_bool(  settings, PROP_AUTO_RECONNECT, true);
    obs_data_set_default_string(settings, PROP_STATUS,         "Not connected");
}

/* ── Source info registration ───────────────────────────────────────────────── */

struct obs_source_info camari_source_info = {
    .id             = "camari_source",
    .type           = OBS_SOURCE_TYPE_INPUT,
    .output_flags   = OBS_SOURCE_VIDEO | OBS_SOURCE_ASYNC,
    .icon_type      = OBS_ICON_TYPE_CAMERA,
    .get_name       = camari_get_name,
    .create         = camari_create,
    .destroy        = camari_destroy,
    .get_width      = camari_get_width,
    .get_height     = camari_get_height,
    .video_tick     = camari_video_tick,
    .get_properties = camari_get_properties,
    .get_defaults   = camari_get_defaults,
    .update         = camari_update,
};
