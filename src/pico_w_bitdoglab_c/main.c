#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "pico/stdlib.h"
#include "hardware/pio.h"
#include "hardware/uart.h"
#include "ws2812.pio.h"

#define MATRIX_PIN 7
#define NUM_PIXELS 25
#define IS_RGBW false

#define UART_ID uart0
#define UART_BAUD 115200
#define UART_TX_PIN 0
#define UART_RX_PIN 1

#define DEFAULT_BRIGHTNESS 7
#define DEFAULT_DURATION_MS 120000

typedef struct {
    bool lamp_on;
    bool manual_mode;
    uint8_t brightness;
    absolute_time_t auto_off_at;
    bool auto_off_active;
} lamp_state_t;

static PIO pio = pio0;
static int sm = 0;
static lamp_state_t lamp_state = {
    .lamp_on = false,
    .manual_mode = false,
    .brightness = DEFAULT_BRIGHTNESS,
    .auto_off_active = false,
};

static void put_pixel(uint32_t pixel_grb) {
    pio_sm_put_blocking(pio, sm, pixel_grb << 8u);
}

static uint32_t urgb_u32(uint8_t r, uint8_t g, uint8_t b) {
    return ((uint32_t)r << 8) | ((uint32_t)g << 16) | (uint32_t)b;
}

static uint8_t brightness_to_component(uint8_t level) {
    if (level < 1) {
        level = 1;
    }
    if (level > 10) {
        level = 10;
    }
    return (uint8_t)((255 * level) / 10);
}

static void matrix_fill_white(uint8_t brightness) {
    uint8_t component = brightness_to_component(brightness);
    uint32_t pixel = urgb_u32(component, component, component);

    for (int i = 0; i < NUM_PIXELS; ++i) {
        put_pixel(pixel);
    }
}

static void matrix_clear(void) {
    for (int i = 0; i < NUM_PIXELS; ++i) {
        put_pixel(0);
    }
}

static void lamp_off(void) {
    lamp_state.lamp_on = false;
    lamp_state.manual_mode = false;
    lamp_state.auto_off_active = false;
    matrix_clear();
}

static void lamp_on_for(uint32_t duration_ms, uint8_t brightness, bool manual_mode) {
    lamp_state.brightness = brightness;
    lamp_state.lamp_on = true;
    lamp_state.manual_mode = manual_mode;
    lamp_state.auto_off_active = true;
    lamp_state.auto_off_at = make_timeout_time_ms(duration_ms);
    matrix_fill_white(lamp_state.brightness);
}

static void uart_send_line(const char *line) {
    uart_puts(UART_ID, line);
    uart_puts(UART_ID, "\n");
}

static void send_status(void) {
    char buffer[96];
    snprintf(
        buffer,
        sizeof(buffer),
        "STATUS lamp=%d brightness=%u manual=%d",
        lamp_state.lamp_on ? 1 : 0,
        lamp_state.brightness,
        lamp_state.manual_mode ? 1 : 0
    );
    uart_send_line(buffer);
}

static void trim_line(char *line) {
    size_t len = strlen(line);
    while (len > 0 && isspace((unsigned char)line[len - 1])) {
        line[len - 1] = '\0';
        --len;
    }
}

static void handle_command(char *line) {
    trim_line(line);
    if (line[0] == '\0') {
        return;
    }

    char *token = strtok(line, " ");
    if (!token) {
        return;
    }

    if (strcmp(token, "HELLO") == 0) {
        uart_send_line("READY BITDOGLAB");
        send_status();
        return;
    }

    if (strcmp(token, "STATUS?") == 0) {
        send_status();
        return;
    }

    if (strcmp(token, "BRIGHTNESS") == 0) {
        char *value = strtok(NULL, " ");
        if (value) {
          int parsed = atoi(value);
          if (parsed < 1) {
              parsed = 1;
          }
          if (parsed > 10) {
              parsed = 10;
          }
          lamp_state.brightness = (uint8_t)parsed;
          if (lamp_state.lamp_on) {
              matrix_fill_white(lamp_state.brightness);
          }
        }
        send_status();
        return;
    }

    if (strcmp(token, "LAMP") == 0) {
        char *action = strtok(NULL, " ");
        if (!action) {
            return;
        }

        if (strcmp(action, "ON") == 0) {
            char *duration_arg = strtok(NULL, " ");
            char *brightness_arg = strtok(NULL, " ");
            char *manual_arg = strtok(NULL, " ");

            uint32_t duration_ms = duration_arg ? (uint32_t)strtoul(duration_arg, NULL, 10) : DEFAULT_DURATION_MS;
            int brightness = brightness_arg ? atoi(brightness_arg) : lamp_state.brightness;
            bool manual_mode = manual_arg ? atoi(manual_arg) != 0 : false;

            if (brightness < 1) {
                brightness = 1;
            }
            if (brightness > 10) {
                brightness = 10;
            }

            lamp_on_for(duration_ms, (uint8_t)brightness, manual_mode);
            send_status();
            return;
        }

        if (strcmp(action, "OFF") == 0) {
            lamp_off();
            send_status();
        }
    }
}

static void poll_uart(void) {
    static char line_buffer[128];
    static size_t line_length = 0;

    while (uart_is_readable(UART_ID)) {
        char c = (char)uart_getc(UART_ID);
        if (c == '\r') {
            continue;
        }

        if (c == '\n') {
            line_buffer[line_length] = '\0';
            handle_command(line_buffer);
            line_length = 0;
            continue;
        }

        if (line_length + 1 < sizeof(line_buffer)) {
            line_buffer[line_length++] = c;
        } else {
            line_length = 0;
        }
    }
}

int main(void) {
    stdio_init_all();

    uart_init(UART_ID, UART_BAUD);
    gpio_set_function(UART_TX_PIN, GPIO_FUNC_UART);
    gpio_set_function(UART_RX_PIN, GPIO_FUNC_UART);

    uint offset = pio_add_program(pio, &ws2812_program);
    ws2812_program_init(pio, sm, offset, MATRIX_PIN, 800000, IS_RGBW);

    lamp_off();
    sleep_ms(200);
    uart_send_line("READY BITDOGLAB");

    while (true) {
        poll_uart();

        if (lamp_state.lamp_on && lamp_state.auto_off_active &&
            absolute_time_diff_us(get_absolute_time(), lamp_state.auto_off_at) <= 0) {
            lamp_off();
            send_status();
        }

        sleep_ms(20);
    }
}
