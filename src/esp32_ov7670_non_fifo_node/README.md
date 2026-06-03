# ESP32 + OV7670 sem FIFO

Este sketch foi preparado para **ESP32 comum + OV7670 sem FIFO**.

Arquivo principal:
- `esp32_ov7670_non_fifo_node.ino`

## Bibliotecas esperadas

Voce precisa ter a biblioteca `OV7670-ESP32` instalada no Arduino IDE:
- `OV7670.h`

O sketch nao depende mais de `BMP.h`, porque o cabecalho BMP ja e montado dentro do proprio `.ino`.

A referencia principal para isso e o projeto `OV7670-ESP32` do kobatan, e a familia `ESP32CameraI2S` do bitluni.

## Ajustes locais na biblioteca

No computador atual, a biblioteca instalada em `Documentos/Arduino/libraries/OV7670-ESP32` precisou de pequenos ajustes para compilar com o core `ESP32 3.3.8`:
- `I2Scamera.c`: inclusao de `Arduino.h`
- `I2Scamera.c`: troca de `gpio_matrix_in(...)` por `pinMatrixInAttach(...)`
- `OV7670.cpp`: troca de `ledcSetup/ledcAttachPin/ledcDetachPin` pela API nova `ledcAttachChannel/ledcWrite(pin,...)/ledcDetach`

Esses ajustes foram feitos na copia local da biblioteca do Arduino IDE e **nao ficam versionados automaticamente neste repositorio**. Se for continuar em outro computador, pode ser necessario repetir esse patch na biblioteca instalada la.

## Pinagem usada neste sketch

- `SIOD -> GPIO 21`
- `SIOC -> GPIO 22`
- `VSYNC -> GPIO 34`
- `XCLK -> GPIO 32`
- `PCLK -> GPIO 33`
- `D0 -> GPIO 27`
- `D1 -> GPIO 17`
- `D2 -> GPIO 16`
- `D3 -> GPIO 15`
- `D4 -> GPIO 14`
- `D5 -> GPIO 13`
- `D6 -> GPIO 12`
- `D7 -> GPIO 4`
- `RESET -> 3.3V` ou `EN`, conforme o seu modulo
- `PWDN -> GND`
- `HREF -> sem uso direto nesta biblioteca`

## Rotas

- `/` preview da camera
- `/capture.bmp?reason=manual` retorna uma foto BMP
- `/status` retorna status em JSON

## Observacoes importantes

- `OV7670 sem FIFO` nao usa `esp_camera.h`
- a resolucao configurada no sketch esta em `QQVGA_RGB565`
- para acesso pelo app depois, o caminho mais simples e o backend buscar `/capture.bmp`
- este sketch e diferente do no `ESP32-CAM`, porque o hardware tambem e diferente
- no teste atual, o ESP32 conectou ao Wi-Fi e travou antes de subir as rotas HTTP, o que indica que o proximo debug deve focar em inicializacao da camera, pinagem e alimentacao
