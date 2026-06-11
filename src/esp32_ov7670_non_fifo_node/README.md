# ESP32 + OV7670 sem FIFO

Este sketch foi preparado para **ESP32 comum + OV7670 sem FIFO**.

Arquivo principal:
- `esp32_ov7670_non_fifo_node.ino`

## Biblioteca

A versao compatível da biblioteca I2S/OV7670 está versionada junto ao sketch. Mantenha
todos os arquivos `.h` e `.cpp` desta pasta no mesmo diretório ao abrir no Arduino IDE.

## Pinagem usada neste sketch

- `SIOD -> GPIO 21`
- `SIOC -> GPIO 22`
- `VSYNC -> GPIO 34`
- `XCLK -> GPIO 32`
- `PCLK -> GPIO 33`
- `D0 -> GPIO 27`
- `D1 -> GPIO 5`
- `D2 -> GPIO 2`
- `D3 -> GPIO 15`
- `D4 -> GPIO 14`
- `D5 -> GPIO 13`
- `D6 -> GPIO 12`
- `D7 -> GPIO 4`
- `RESET -> 3.3V` ou `EN`, conforme o seu modulo
- `PWDN -> GND`
- `HREF -> GPIO 35`

## Rotas

- `/` preview da camera
- `/capture.bmp?reason=manual` retorna uma foto BMP
- `/status` retorna status em JSON

## Fluxo remoto

1. A camera consulta `poll-camera-command`.
2. Ao receber `capture_photo`, captura um frame.
3. Monta um BMP compacto em memoria.
4. Envia o BMP para `upload-photo`.
5. O app lista e baixa a foto pela funcao `photos`.

## Observacoes importantes

- `OV7670 sem FIFO` nao usa `esp_camera.h`
- a resolucao configurada esta em `QQQVGA_RGB565` (`80x60`)
- o recorte final enviado possui `72x49` pixels para preservar memoria durante HTTPS
- este sketch e diferente do no `ESP32-CAM`, porque o hardware tambem e diferente
- o fluxo local e remoto foi validado com capturas reais
