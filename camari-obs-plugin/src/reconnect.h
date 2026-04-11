#pragma once

#include <stdint.h>
#include <stdbool.h>

/*
 * Reconnect scheduler.
 *
 * After a disconnection, call reconnect_schedule() from video_tick().
 * The caller should check reconnect_due() each tick and, if true, start
 * a new stream thread and call reconnect_cancel().
 */

typedef struct {
    bool     pending;
    uint64_t fire_at_ns;  /* os_gettime_ns() value at which reconnect is due */
} ReconnectState;

#define RECONNECT_DELAY_NS (2ULL * 1000000000ULL)  /* 2 seconds */

/* Schedule a reconnect attempt in RECONNECT_DELAY_NS nanoseconds. */
void reconnect_schedule(ReconnectState *r, uint64_t now_ns);

/* Returns true if a reconnect is pending and the delay has elapsed. */
bool reconnect_due(const ReconnectState *r, uint64_t now_ns);

/* Cancel any pending reconnect (call after successfully reconnecting). */
void reconnect_cancel(ReconnectState *r);
