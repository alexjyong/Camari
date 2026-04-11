#include "mjpeg-parser.h"

#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <stdio.h>
#include <ctype.h>

#define BOUNDARY        "--frame"
#define BOUNDARY_LEN    7
#define INITIAL_CAP     (256 * 1024) /* 256 KB */

/* ── Internal helpers ──────────────────────────────────────────────────────── */

static bool buf_append(MjpegParser *p, const uint8_t *data, size_t len)
{
    size_t need = p->buf_len + len;
    if (need > p->buf_cap) {
        size_t new_cap = p->buf_cap * 2;
        while (new_cap < need) new_cap *= 2;
        uint8_t *nb = realloc(p->buf, new_cap);
        if (!nb) return false;
        p->buf     = nb;
        p->buf_cap = new_cap;
    }
    memcpy(p->buf + p->buf_len, data, len);
    p->buf_len += len;
    return true;
}

static void buf_consume(MjpegParser *p, size_t n)
{
    if (n >= p->buf_len) {
        p->buf_len = 0;
        return;
    }
    memmove(p->buf, p->buf + n, p->buf_len - n);
    p->buf_len -= n;
}

/* Find first occurrence of needle in buf. Returns offset or -1. */
static int buf_find(const uint8_t *buf, size_t buf_len,
                    const char *needle, size_t needle_len)
{
    if (needle_len > buf_len) return -1;
    for (size_t i = 0; i <= buf_len - needle_len; i++) {
        if (memcmp(buf + i, needle, needle_len) == 0)
            return (int)i;
    }
    return -1;
}

/* Parse a single header line. Returns true if it was Content-Length. */
static bool parse_header_line(MjpegParser *p, const char *line)
{
    if (strncasecmp(line, "Content-Length:", 15) == 0) {
        p->content_length = atoi(line + 15);
        return true;
    }
    return false;
}

/* ── Public API ────────────────────────────────────────────────────────────── */

void mjpeg_parser_init(MjpegParser *p, mjpeg_frame_cb on_frame, void *userdata)
{
    memset(p, 0, sizeof(*p));
    p->buf           = malloc(INITIAL_CAP);
    p->buf_cap       = p->buf ? INITIAL_CAP : 0;
    p->content_length = -1;
    p->on_frame      = on_frame;
    p->userdata      = userdata;
    p->phase         = MJPEG_SEEKING_BOUNDARY;
}

void mjpeg_parser_reset(MjpegParser *p)
{
    p->buf_len        = 0;
    p->content_length = -1;
    p->phase          = MJPEG_SEEKING_BOUNDARY;
}

void mjpeg_parser_destroy(MjpegParser *p)
{
    free(p->buf);
    p->buf     = NULL;
    p->buf_cap = 0;
    p->buf_len = 0;
}

void mjpeg_parser_feed(MjpegParser *p, const uint8_t *data, size_t len)
{
    if (!p->buf || !data || len == 0) return;
    if (!buf_append(p, data, len)) return;

    bool progress = true;
    while (progress && p->buf_len > 0) {
        progress = false;

        if (p->phase == MJPEG_SEEKING_BOUNDARY) {
            int off = buf_find(p->buf, p->buf_len, BOUNDARY, BOUNDARY_LEN);
            if (off < 0) {
                /* Keep last BOUNDARY_LEN-1 bytes in case boundary straddles */
                if (p->buf_len >= BOUNDARY_LEN) {
                    size_t keep = BOUNDARY_LEN - 1;
                    buf_consume(p, p->buf_len - keep);
                }
                break;
            }
            /* Skip past boundary and trailing \r\n */
            buf_consume(p, off + BOUNDARY_LEN);
            /* Skip optional \r\n after boundary line */
            if (p->buf_len >= 2 && p->buf[0] == '\r' && p->buf[1] == '\n')
                buf_consume(p, 2);
            else if (p->buf_len >= 1 && p->buf[0] == '\n')
                buf_consume(p, 1);

            p->content_length = -1;
            p->phase          = MJPEG_READING_HEADERS;
            progress          = true;
        }

        if (p->phase == MJPEG_READING_HEADERS) {
            /* Process header lines until blank line */
            while (true) {
                /* Find end of line */
                int eol = buf_find(p->buf, p->buf_len, "\r\n", 2);
                if (eol < 0) break; /* need more data */

                if (eol == 0) {
                    /* Blank line = end of headers */
                    buf_consume(p, 2);
                    p->phase = MJPEG_READING_BODY;
                    progress = true;
                    break;
                }

                /* Parse header line */
                char line[512];
                size_t copy = (size_t)eol < sizeof(line) - 1 ? (size_t)eol : sizeof(line) - 1;
                memcpy(line, p->buf, copy);
                line[copy] = '\0';
                parse_header_line(p, line);
                buf_consume(p, eol + 2);
            }
        }

        if (p->phase == MJPEG_READING_BODY) {
            if (p->content_length < 0) {
                /* No Content-Length — find JPEG EOI marker (0xFF 0xD9) as fallback */
                for (size_t i = 0; i + 1 < p->buf_len; i++) {
                    if (p->buf[i] == 0xFF && p->buf[i + 1] == 0xD9) {
                        size_t frame_len = i + 2;
                        if (p->on_frame)
                            p->on_frame(p->buf, frame_len, p->userdata);
                        buf_consume(p, frame_len);
                        p->phase = MJPEG_SEEKING_BOUNDARY;
                        progress = true;
                        break;
                    }
                }
            } else if (p->buf_len >= (size_t)p->content_length) {
                if (p->on_frame)
                    p->on_frame(p->buf, (size_t)p->content_length, p->userdata);
                buf_consume(p, (size_t)p->content_length);
                /* Skip trailing \r\n after body */
                if (p->buf_len >= 2 && p->buf[0] == '\r' && p->buf[1] == '\n')
                    buf_consume(p, 2);
                p->content_length = -1;
                p->phase          = MJPEG_SEEKING_BOUNDARY;
                progress          = true;
            }
        }
    }
}
