# Teste simples de UART entre ESP32 e BitDogLab

Arquivos:

- [`src/esp32_uart_link_test/esp32_uart_link_test.ino`](C:\Users\fakef\OneDrive\Documentos\New project\pet_monitor_iot\src\esp32_uart_link_test\esp32_uart_link_test.ino)
- [`src/pico_w_uart_test_c/main.c`](C:\Users\fakef\OneDrive\Documentos\New project\pet_monitor_iot\src\pico_w_uart_test_c\main.c)

## Ligacao

- `ESP32 GPIO13 (TX2)` -> `Pico GP9 (RX)`
- `ESP32 GPIO16 (RX2)` <- `Pico GP8 (TX)`
- `GND ESP32` -> `GND Pico`

## O que esperar

### No ESP32

No monitor serial a `115200`, ele envia automaticamente:

- `PING 1`
- `PING 2`
- `PING 3`

Se a ligacao estiver correta, voce vai ver respostas como:

- `PICO_READY`
- `PONG -> PING 1`

Tambem pode digitar:

- `P` para enviar um `PING` manual
- `H` para enviar `HELLO_FROM_ESP32`

### Na BitDogLab / Pico

Pelo USB dela, se abrir o serial USB, vai aparecer:

- `=== TESTE UART BITDOGLAB/PICO ===`
- `UART1 TX=GP8 RX=GP9 @ 115200`

Quando receber dados do ESP32, ela responde:

- `HELLO_FROM_PICO`
- `PONG -> PING X`
- ou `ECHO -> ...`

## Objetivo

Esse teste serve apenas para validar:

- se TX/RX estao cruzados corretamente
- se o `GND` comum foi ligado
- se as duas placas conseguem trocar texto por UART
