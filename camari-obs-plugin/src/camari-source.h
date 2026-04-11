#pragma once

#include <obs-module.h>
#include <util/threading.h>
#include <stdint.h>
#include <stdbool.h>

/* ── Connection state ──────────────────────────────────────────────────────── */

typedef enum {
    CAMARI_STATE_DISCONNECTED = 0,
    CAMARI_STATE_CONNECTING,
    CAMARI_STATE_CONNECTED,
} CamariConnectionState;

/* ── Per-source configuration (persisted by OBS) ───────────────────────────── */

typedef struct {
    char     ip_address[256];
    int      port;
    bool     auto_reconnect;
} CamariSourceConfig;

/* ── Per-source runtime instance ────────────────────────────────────────────── */

typedef struct {
    obs_source_t        *source;
    CamariSourceConfig   config;

    /* Connection state — written by stream thread, read by video_tick */
    volatile CamariConnectionState state;

    /* Decoded frame dimensions — set on first frame, updated on resolution change */
    uint32_t             frame_width;
    uint32_t             frame_height;

    /* RGBA frame buffer — reallocated on resolution change; guarded by frame_mutex */
    uint8_t             *frame_buffer;
    size_t               frame_buffer_size;
    pthread_mutex_t      frame_mutex;

    /* Stream thread */
    pthread_t            stream_thread;
    volatile bool        thread_stop;   /* set to true to request thread exit */
    bool                 thread_running;

    /* Reconnect scheduling */
    volatile bool        reconnect_pending;
    uint64_t             reconnect_at_ns; /* os_gettime_ns() target */
} CamariSourceInstance;

/* ── obs_source_info registration ───────────────────────────────────────────── */

extern struct obs_source_info camari_source_info;
