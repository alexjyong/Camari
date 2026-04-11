#pragma once

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

/*
 * MJPEG multipart parser.
 *
 * Camari's HTTP server sends:
 *   Content-Type: multipart/x-mixed-replace; boundary=frame
 *
 * Each part looks like:
 *   --frame\r\n
 *   Content-Type: image/jpeg\r\n
 *   Content-Length: <N>\r\n
 *   \r\n
 *   <N bytes of JPEG data>
 *   \r\n
 *
 * Feed incoming data with mjpeg_parser_feed(). The on_frame callback fires
 * once per complete JPEG frame.
 */

typedef void (*mjpeg_frame_cb)(const uint8_t *jpeg, size_t len, void *userdata);

typedef enum {
    MJPEG_SEEKING_BOUNDARY = 0, /* scanning for "--frame" */
    MJPEG_READING_HEADERS,      /* consuming header lines until blank line */
    MJPEG_READING_BODY,         /* consuming Content-Length bytes of JPEG */
} MjpegParsePhase;

typedef struct {
    MjpegParsePhase  phase;

    /* Accumulation buffer */
    uint8_t         *buf;
    size_t           buf_len;
    size_t           buf_cap;

    /* From Content-Length header (-1 = not found yet) */
    int              content_length;

    /* Callback invoked per complete frame */
    mjpeg_frame_cb   on_frame;
    void            *userdata;
} MjpegParser;

/* Initialise parser. on_frame is called for each complete JPEG. */
void mjpeg_parser_init(MjpegParser *p, mjpeg_frame_cb on_frame, void *userdata);

/* Feed incoming bytes. May call on_frame zero or more times. */
void mjpeg_parser_feed(MjpegParser *p, const uint8_t *data, size_t len);

/* Reset parser state (e.g. on reconnect). */
void mjpeg_parser_reset(MjpegParser *p);

/* Free internal buffer. */
void mjpeg_parser_destroy(MjpegParser *p);
