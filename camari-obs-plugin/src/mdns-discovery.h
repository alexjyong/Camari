#pragma once

#include <stdbool.h>

typedef struct {
    char name[256];    /* NSD instance name e.g. "Camari", "Camari (2)" */
    char address[64];  /* IPv4 string e.g. "192.168.1.42" */
    int  port;         /* port from SRV record */
} CamariDevice;

typedef struct {
    CamariDevice devices[16];
    int          count;
} CamariDiscoveryResult;

/**
 * Scan the local network for Camari instances via mDNS DNS-SD.
 *
 * Sends a PTR query for _camari._tcp.local, collects responses for
 * timeout_ms milliseconds, and fills `out` with discovered devices.
 *
 * Blocking — call from a thread that can afford to wait timeout_ms.
 *
 * @param timeout_ms  How long to wait for responses (recommended: 2000)
 * @param out         Caller-allocated result; always initialised on return
 * @return  0 on success (count may be 0 if no devices found)
 *         -1 on socket error
 */
int camari_discover(int timeout_ms, CamariDiscoveryResult *out);
