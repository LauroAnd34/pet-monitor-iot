#include <stdio.h>
#include <string.h>

#include "pico/stdlib.h"
#include "hardware/uart.h"

#define UART_ID uart1
#define UART_BAUD 115200
#define UART_TX_PIN 8
#define UART_RX_PIN 9

static void uart_send_line(const char *line) {
    uart_puts(UART_ID, line);
    uart_puts(UART_ID, "\n");
}

static void handle_line(char *line) {
    if (line[0] == '\0') {
        return;
    }

    printf("[PICO] Recebido: %s\n", line);

    if (strcmp(line, "HELLO_FROM_ESP32") == 0) {
        uart_send_line("HELLO_FROM_PICO");
        return;
    }

    if (strncmp(line, "PING", 4) == 0) {
        char response[96];
        snprintf(response, sizeof(response), "PONG -> %s", line);
        uart_send_line(response);
        return;
    }

    char fallback[96];
    snprintf(fallback, sizeof(fallback), "ECHO -> %s", line);
    uart_send_line(fallback);
}

int main(void) {
    stdio_init_all();
    sleep_ms(1500);

    uart_init(UART_ID, UART_BAUD);
    gpio_set_function(UART_TX_PIN, GPIO_FUNC_UART);
    gpio_set_function(UART_RX_PIN, GPIO_FUNC_UART);

    printf("=== TESTE UART BITDOGLAB/PICO ===\n");
    printf("UART1 TX=GP8 RX=GP9 @ 115200\n");
    uart_send_line("PICO_READY");

    char line_buffer[128];
    size_t line_length = 0;

    while (true) {
        while (uart_is_readable(UART_ID)) {
            char c = (char)uart_getc(UART_ID);

            if (c == '\r') {
                continue;
            }

            if (c == '\n') {
                line_buffer[line_length] = '\0';
                handle_line(line_buffer);
                line_length = 0;
                continue;
            }

            if (line_length + 1 < sizeof(line_buffer)) {
                line_buffer[line_length++] = c;
            } else {
                line_length = 0;
            }
        }

        sleep_ms(10);
    }
}
