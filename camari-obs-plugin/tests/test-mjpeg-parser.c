#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "../src/mjpeg-parser.h"

/* ── Test helpers ──────────────────────────────────────────────────────────── */

static int  g_frame_count = 0;
static const uint8_t *g_last_jpeg  = NULL;
static size_t         g_last_len   = 0;
static uint8_t        g_frame_copy[64 * 1024];

static void on_frame(const uint8_t *jpeg, size_t len, void *userdata)
{
    (void)userdata;
    g_frame_count++;
    g_last_len = len;
    if (len <= sizeof(g_frame_copy)) {
        memcpy(g_frame_copy, jpeg, len);
        g_last_jpeg = g_frame_copy;
    }
}

static void reset_counters(void)
{
    g_frame_count = 0;
    g_last_jpeg   = NULL;
    g_last_len    = 0;
}

/* Build a minimal MJPEG multipart chunk for a fake JPEG payload. */
static void build_chunk(uint8_t *out, size_t *out_len,
                         const uint8_t *jpeg, size_t jpeg_len)
{
    char header[256];
    int hlen = snprintf(header, sizeof(header),
        "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: %zu\r\n\r\n",
        jpeg_len);
    memcpy(out, header, hlen);
    memcpy(out + hlen, jpeg, jpeg_len);
    memcpy(out + hlen + jpeg_len, "\r\n", 2);
    *out_len = hlen + jpeg_len + 2;
}

/* ── Tests ─────────────────────────────────────────────────────────────────── */

static void test_single_frame(void)
{
    printf("test_single_frame... ");
    MjpegParser p;
    mjpeg_parser_init(&p, on_frame, NULL);
    reset_counters();

    uint8_t fake_jpeg[] = {0xFF, 0xD8, 0xAA, 0xBB, 0xFF, 0xD9};
    uint8_t chunk[512];
    size_t chunk_len;
    build_chunk(chunk, &chunk_len, fake_jpeg, sizeof(fake_jpeg));

    mjpeg_parser_feed(&p, chunk, chunk_len);

    assert(g_frame_count == 1);
    assert(g_last_len == sizeof(fake_jpeg));
    assert(memcmp(g_last_jpeg, fake_jpeg, sizeof(fake_jpeg)) == 0);

    mjpeg_parser_destroy(&p);
    printf("PASS\n");
}

static void test_multi_frame_single_feed(void)
{
    printf("test_multi_frame_single_feed... ");
    MjpegParser p;
    mjpeg_parser_init(&p, on_frame, NULL);
    reset_counters();

    uint8_t fake_jpeg[] = {0xFF, 0xD8, 0x01, 0xFF, 0xD9};
    uint8_t stream[2048];
    size_t  pos = 0;

    for (int i = 0; i < 3; i++) {
        size_t chunk_len;
        build_chunk(stream + pos, &chunk_len, fake_jpeg, sizeof(fake_jpeg));
        pos += chunk_len;
    }

    mjpeg_parser_feed(&p, stream, pos);

    assert(g_frame_count == 3);

    mjpeg_parser_destroy(&p);
    printf("PASS\n");
}

static void test_frame_split_across_calls(void)
{
    printf("test_frame_split_across_calls... ");
    MjpegParser p;
    mjpeg_parser_init(&p, on_frame, NULL);
    reset_counters();

    uint8_t fake_jpeg[] = {0xFF, 0xD8, 0x55, 0x66, 0xFF, 0xD9};
    uint8_t chunk[512];
    size_t chunk_len;
    build_chunk(chunk, &chunk_len, fake_jpeg, sizeof(fake_jpeg));

    /* Feed in two halves */
    size_t half = chunk_len / 2;
    mjpeg_parser_feed(&p, chunk, half);
    assert(g_frame_count == 0);

    mjpeg_parser_feed(&p, chunk + half, chunk_len - half);
    assert(g_frame_count == 1);
    assert(g_last_len == sizeof(fake_jpeg));

    mjpeg_parser_destroy(&p);
    printf("PASS\n");
}

static void test_no_content_length_uses_eoi(void)
{
    printf("test_no_content_length_uses_eoi... ");
    MjpegParser p;
    mjpeg_parser_init(&p, on_frame, NULL);
    reset_counters();

    /* Build chunk without Content-Length */
    const char *header = "--frame\r\nContent-Type: image/jpeg\r\n\r\n";
    uint8_t fake_jpeg[] = {0xFF, 0xD8, 0xCC, 0xFF, 0xD9};
    uint8_t chunk[512];
    size_t hlen = strlen(header);
    memcpy(chunk, header, hlen);
    memcpy(chunk + hlen, fake_jpeg, sizeof(fake_jpeg));
    size_t total = hlen + sizeof(fake_jpeg);

    mjpeg_parser_feed(&p, chunk, total);

    assert(g_frame_count == 1);
    assert(g_last_len == sizeof(fake_jpeg));

    mjpeg_parser_destroy(&p);
    printf("PASS\n");
}

static void test_reset_clears_state(void)
{
    printf("test_reset_clears_state... ");
    MjpegParser p;
    mjpeg_parser_init(&p, on_frame, NULL);
    reset_counters();

    uint8_t fake_jpeg[] = {0xFF, 0xD8, 0xFF, 0xD9};
    uint8_t chunk[512];
    size_t chunk_len;
    build_chunk(chunk, &chunk_len, fake_jpeg, sizeof(fake_jpeg));

    /* Feed half, then reset */
    mjpeg_parser_feed(&p, chunk, chunk_len / 2);
    assert(g_frame_count == 0);

    mjpeg_parser_reset(&p);

    /* Feed a complete fresh chunk — should produce exactly one frame */
    mjpeg_parser_feed(&p, chunk, chunk_len);
    assert(g_frame_count == 1);

    mjpeg_parser_destroy(&p);
    printf("PASS\n");
}

int main(void)
{
    printf("=== MJPEG Parser Tests ===\n");
    test_single_frame();
    test_multi_frame_single_feed();
    test_frame_split_across_calls();
    test_no_content_length_uses_eoi();
    test_reset_clears_state();
    printf("All MJPEG parser tests passed.\n");
    return 0;
}
