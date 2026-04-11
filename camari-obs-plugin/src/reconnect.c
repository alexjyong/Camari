#include "reconnect.h"

void reconnect_schedule(ReconnectState *r, uint64_t now_ns)
{
    if (!r->pending) {
        r->pending    = true;
        r->fire_at_ns = now_ns + RECONNECT_DELAY_NS;
    }
}

bool reconnect_due(const ReconnectState *r, uint64_t now_ns)
{
    return r->pending && now_ns >= r->fire_at_ns;
}

void reconnect_cancel(ReconnectState *r)
{
    r->pending    = false;
    r->fire_at_ns = 0;
}
