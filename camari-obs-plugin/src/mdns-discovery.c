/*
 * mdns-discovery.c
 *
 * Discovers Camari devices on the local network using mDNS DNS-SD.
 * Sends a PTR query for _camari._tcp.local and correlates the PTR, SRV,
 * and A records returned within the scan window.
 *
 * Uses mjansson/mdns (single public-domain C header).
 * MDNS_IMPLEMENTATION must be defined in exactly one translation unit.
 */

#ifdef _WIN32
#  define _WINSOCK_DEPRECATED_NO_WARNINGS
#  include <winsock2.h>
#  include <ws2tcpip.h>
#endif

#define MDNS_IMPLEMENTATION
#include "mdns.h"

#include "mdns-discovery.h"

#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#ifdef _WIN32
#  include <windows.h>   /* GetTickCount64 */
#else
#  include <time.h>
#  include <sys/time.h>
#endif

/* ── Portable millisecond clock ────────────────────────────────────────────── */

static long long now_ms(void)
{
#ifdef _WIN32
    return (long long)GetTickCount64();
#else
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long long)ts.tv_sec * 1000LL + ts.tv_nsec / 1000000LL;
#endif
}

/* ── Per-instance accumulator ──────────────────────────────────────────────── */

#define MAX_ENTRIES 16

typedef struct {
    char instance_name[256];  /* from PTR record  e.g. "Camari"           */
    char srv_target[256];     /* from SRV record  e.g. "Android.local."   */
    int  port;                /* from SRV record                           */
    char address[64];         /* from A record matching srv_target         */
    bool has_srv;
    bool has_addr;
} DiscoveryEntry;

typedef struct {
    DiscoveryEntry entries[MAX_ENTRIES];
    int            count;
    char           scratch[2048]; /* reused by mdns string helpers         */
} ScanContext;

/* ── mdns.h record callback ────────────────────────────────────────────────── */

static mdns_string_t entry_to_string(const void *buf, size_t size,
                                     size_t offset, size_t length)
{
    /* mdns_string_t is just a ptr+len pair; we can wrap the raw buffer. */
    (void)size;
    mdns_string_t s;
    s.str    = (const char *)buf + offset;
    s.length = length;
    return s;
}

static int record_callback(int sock, const struct sockaddr *from,
                            size_t addrlen,
                            mdns_entry_type_t entry_type,
                            uint16_t query_id,
                            uint16_t rtype, uint16_t rclass,
                            uint32_t ttl,
                            const void *data, size_t size,
                            size_t name_offset, size_t name_length,
                            size_t record_offset, size_t record_length,
                            void *user_data)
{
    (void)sock; (void)from; (void)addrlen; (void)entry_type;
    (void)query_id; (void)rclass; (void)ttl;

    ScanContext *ctx = (ScanContext *)user_data;

    if (rtype == MDNS_RECORDTYPE_PTR) {
        /* PTR: name = _camari._tcp.local., rdata = instance._camari._tcp.local. */
        mdns_string_t instance = mdns_record_parse_ptr(
            data, size, record_offset, record_length,
            ctx->scratch, sizeof(ctx->scratch));

        /* Strip the trailing service type to get just the instance name.
           e.g. "Camari._camari._tcp.local." → "Camari"                   */
        char inst[256] = {0};
        size_t copy = instance.length < sizeof(inst) - 1
                    ? instance.length : sizeof(inst) - 1;
        memcpy(inst, instance.str, copy);
        inst[copy] = '\0';
        /* Truncate at the first '.' that marks the service type boundary  */
        char *dot = strchr(inst, '.');
        if (dot) *dot = '\0';

        /* Find or create an accumulator slot for this instance            */
        for (int i = 0; i < ctx->count; i++) {
            if (strcmp(ctx->entries[i].instance_name, inst) == 0)
                return 0; /* already have a slot */
        }
        if (ctx->count < MAX_ENTRIES) {
            strncpy(ctx->entries[ctx->count].instance_name, inst,
                    sizeof(ctx->entries[ctx->count].instance_name) - 1);
            ctx->count++;
        }

    } else if (rtype == MDNS_RECORDTYPE_SRV) {
        /* SRV: name = instance._camari._tcp.local., rdata = priority weight port target */
        mdns_record_srv_t srv = mdns_record_parse_srv(
            data, size, record_offset, record_length,
            ctx->scratch, sizeof(ctx->scratch));

        /* Extract instance name from the record name field                */
        mdns_string_t rec_name = mdns_record_parse_ptr(
            data, size, name_offset, name_length,
            ctx->scratch, sizeof(ctx->scratch));
        char inst[256] = {0};
        size_t nc = rec_name.length < sizeof(inst) - 1
                  ? rec_name.length : sizeof(inst) - 1;
        memcpy(inst, rec_name.str, nc);
        inst[nc] = '\0';
        char *dot = strchr(inst, '.');
        if (dot) *dot = '\0';

        char target[256] = {0};
        size_t tc = srv.name.length < sizeof(target) - 1
                  ? srv.name.length : sizeof(target) - 1;
        memcpy(target, srv.name.str, tc);
        target[tc] = '\0';

        for (int i = 0; i < ctx->count; i++) {
            if (strcmp(ctx->entries[i].instance_name, inst) == 0) {
                strncpy(ctx->entries[i].srv_target, target,
                        sizeof(ctx->entries[i].srv_target) - 1);
                ctx->entries[i].port    = (int)srv.port;
                ctx->entries[i].has_srv = true;
                break;
            }
        }

    } else if (rtype == MDNS_RECORDTYPE_A) {
        /* A record: name = hostname.local., rdata = IPv4 address          */
        struct sockaddr_in addr;
        mdns_record_parse_a(data, size, record_offset, record_length, &addr);

        char ip[64] = {0};
#ifdef _WIN32
        InetNtopA(AF_INET, &addr.sin_addr, ip, sizeof(ip));
#else
        inet_ntop(AF_INET, &addr.sin_addr, ip, sizeof(ip));
#endif

        /* A record name (the hostname it resolves)                        */
        mdns_string_t rec_name = mdns_record_parse_ptr(
            data, size, name_offset, name_length,
            ctx->scratch, sizeof(ctx->scratch));
        char hostname[256] = {0};
        size_t hn = rec_name.length < sizeof(hostname) - 1
                  ? rec_name.length : sizeof(hostname) - 1;
        memcpy(hostname, rec_name.str, hn);
        hostname[hn] = '\0';

        /* Match this hostname against SRV targets in all entries          */
        for (int i = 0; i < ctx->count; i++) {
            if (ctx->entries[i].has_srv &&
                strncmp(ctx->entries[i].srv_target, hostname,
                        strlen(ctx->entries[i].srv_target)) == 0) {
                strncpy(ctx->entries[i].address, ip,
                        sizeof(ctx->entries[i].address) - 1);
                ctx->entries[i].has_addr = true;
            }
        }
    }

    return 0;
}

/* ── Public API ────────────────────────────────────────────────────────────── */

int camari_discover(int timeout_ms, CamariDiscoveryResult *out)
{
    memset(out, 0, sizeof(*out));

#ifdef _WIN32
    WSADATA wsa;
    WSAStartup(MAKEWORD(2, 2), &wsa);
#endif

    int sock = mdns_socket_open_ipv4(NULL);
    if (sock < 0) {
#ifdef _WIN32
        WSACleanup();
#endif
        return -1;
    }

    /* Buffer for send and receive operations */
    uint8_t buf[2048];
    static const char service[] = "_camari._tcp.local.";

    uint16_t query_id = mdns_query_send(sock, MDNS_RECORDTYPE_PTR,
                                        service, sizeof(service) - 1,
                                        buf, sizeof(buf), 0);
    if (query_id < 0)
        query_id = 0; /* accept any response if send_id not tracked */

    ScanContext ctx;
    memset(&ctx, 0, sizeof(ctx));

    long long deadline = now_ms() + timeout_ms;

    /* Set receive timeout to 200 ms so we loop and check elapsed time    */
#ifdef _WIN32
    DWORD tv_ms = 200;
    setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO,
               (const char *)&tv_ms, sizeof(tv_ms));
#else
    struct timeval tv = { 0, 200 * 1000 };
    setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
#endif

    while (now_ms() < deadline) {
        mdns_query_recv(sock, buf, sizeof(buf),
                        record_callback, &ctx, (int)query_id);
    }

    mdns_socket_close(sock);

#ifdef _WIN32
    WSACleanup();
#endif

    /* Copy complete entries into the output result                        */
    for (int i = 0; i < ctx.count && out->count < 16; i++) {
        DiscoveryEntry *e = &ctx.entries[i];
        if (e->has_srv && e->has_addr) {
            strncpy(out->devices[out->count].name, e->instance_name,
                    sizeof(out->devices[out->count].name) - 1);
            strncpy(out->devices[out->count].address, e->address,
                    sizeof(out->devices[out->count].address) - 1);
            out->devices[out->count].port = e->port;
            out->count++;
        }
    }

    return 0;
}
