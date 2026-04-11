#include <stdio.h>
#include <assert.h>
#include "../src/reconnect.h"

static void test_reconnect_fires_after_delay(void)
{
    printf("test_reconnect_fires_after_delay... ");
    ReconnectState r = {0};

    uint64_t now = 1000000000ULL; /* 1 s */
    reconnect_schedule(&r, now);

    assert(r.pending == true);
    assert(!reconnect_due(&r, now));                            /* not yet */
    assert(!reconnect_due(&r, now + RECONNECT_DELAY_NS - 1));  /* 1 ns early */
    assert(reconnect_due(&r, now + RECONNECT_DELAY_NS));        /* exactly on time */
    assert(reconnect_due(&r, now + RECONNECT_DELAY_NS + 1));    /* past due */

    printf("PASS\n");
}

static void test_reconnect_skipped_when_already_pending(void)
{
    printf("test_reconnect_skipped_when_already_pending... ");
    ReconnectState r = {0};

    uint64_t t0 = 5000000000ULL;
    reconnect_schedule(&r, t0);
    uint64_t first_fire = r.fire_at_ns;

    /* Schedule again later — should not move the deadline */
    reconnect_schedule(&r, t0 + 1000000000ULL);
    assert(r.fire_at_ns == first_fire);

    printf("PASS\n");
}

static void test_cancel_stops_reconnect(void)
{
    printf("test_cancel_stops_reconnect... ");
    ReconnectState r = {0};

    uint64_t now = 2000000000ULL;
    reconnect_schedule(&r, now);
    assert(r.pending == true);

    reconnect_cancel(&r);
    assert(r.pending == false);
    assert(!reconnect_due(&r, now + RECONNECT_DELAY_NS + 1));

    printf("PASS\n");
}

static void test_can_reschedule_after_cancel(void)
{
    printf("test_can_reschedule_after_cancel... ");
    ReconnectState r = {0};

    uint64_t t0 = 3000000000ULL;
    reconnect_schedule(&r, t0);
    reconnect_cancel(&r);

    uint64_t t1 = t0 + 10000000000ULL; /* 10 s later */
    reconnect_schedule(&r, t1);
    assert(r.pending == true);
    assert(r.fire_at_ns == t1 + RECONNECT_DELAY_NS);
    assert(reconnect_due(&r, t1 + RECONNECT_DELAY_NS));

    printf("PASS\n");
}

int main(void)
{
    printf("=== Reconnect Tests ===\n");
    test_reconnect_fires_after_delay();
    test_reconnect_skipped_when_already_pending();
    test_cancel_stops_reconnect();
    test_can_reschedule_after_cancel();
    printf("All reconnect tests passed.\n");
    return 0;
}
