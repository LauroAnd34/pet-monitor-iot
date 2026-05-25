# BitDogLab Lamp in C

Projeto em `C` para `Raspberry Pi Pico W / BitDogLab` usando `Pico SDK`.

## Arquivos

- [main.c](C:\Users\fakef\OneDrive\Documentos\New project\pet_monitor_iot\src\pico_w_bitdoglab_c\main.c)
- [ws2812.pio](C:\Users\fakef\OneDrive\Documentos\New project\pet_monitor_iot\src\pico_w_bitdoglab_c\ws2812.pio)
- [CMakeLists.txt](C:\Users\fakef\OneDrive\Documentos\New project\pet_monitor_iot\src\pico_w_bitdoglab_c\CMakeLists.txt)

## O que esse codigo faz

- controla a matriz da BitDogLab como lampada branca
- recebe comandos do `ESP32` via `UART`
- responde o status de volta
- desliga automaticamente quando o tempo configurado termina

## UART usada

- `TX`: `GPIO 0`
- `RX`: `GPIO 1`
- `baud rate`: `115200`

## Pino da matriz

Por padrao:

- `MATRIX_PIN = 7`

Se sua BitDogLab usar outro pino para a matriz WS2812, troque no `main.c`.

## Como compilar no VS Code

Com a extensao oficial do Raspberry Pi Pico:

1. Abra esta pasta `pico_w_bitdoglab_c`
2. Garanta que o `Pico SDK` esteja configurado
3. Clique em `Configure Project`
4. Depois em `Build`
5. Grave o `.uf2` gerado na placa em modo `BOOTSEL`

## Comandos esperados do ESP32

- `HELLO`
- `STATUS?`
- `BRIGHTNESS <1-10>`
- `LAMP ON <duracao_ms> <brilho> <manual>`
- `LAMP OFF`
