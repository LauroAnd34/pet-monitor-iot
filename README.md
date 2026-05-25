# Pet Monitor IoT

Projeto base para atividade de IoT usando:

- `ESP32` principal para sensores, servo, LED e painel web
- `ESP32-CAM` para monitoramento visual

O sistema monitora:

- temperatura e umidade
- nivel de racao
- nivel de agua
- luminosidade do ambiente
- presenca do pet
- gas no ambiente

O sistema executa:

- liberacao automatica ou manual de racao
- acionamento remoto do LED
- acionamento automatico do LED quando estiver escuro e houver presenca
- alertas por webhook
- painel web com dados em tempo real
- modo sem internet, com o proprio ESP32 servindo o app

## Estrutura

- [`src/esp32_main/esp32_main.ino`](C:\Users\fakef\OneDrive\Documentos\New project\pet_monitor_iot\src\esp32_main\esp32_main.ino): firmware do ESP32 principal
- [`src/esp32_cam/esp32_cam.ino`](C:\Users\fakef\OneDrive\Documentos\New project\pet_monitor_iot\src\esp32_cam\esp32_cam.ino): firmware do ESP32-CAM

## Bibliotecas

No PlatformIO, as dependencias principais ja estao no `platformio.ini`.

Se for usar Arduino IDE, instale:

- `DHT sensor library`
- `ESP32Servo`
- pacote de placas `ESP32 by Espressif Systems`

## Pinagem sugerida do ESP32 principal

Voce pode alterar no topo do arquivo `esp32_main.ino`.

- `DHT22`: GPIO 4
- `HC-SR04 racao`: trig GPIO 18 / echo GPIO 19
- `HC-SR04 agua`: trig GPIO 5 / echo GPIO 17
- `PIR`: GPIO 27
- `MQ-x gas analogico`: GPIO 34
- `LDR / sensor de luminosidade analogico`: GPIO 35
- `Servo`: GPIO 14
- `LED`: GPIO 2

## Configuracao do sensor de luminosidade

O firmware foi preparado para leitura analogica em `GPIO 35`.

Voce pode usar:

- um `LDR` com divisor de tensao
- um modulo de luminosidade com saida analogica

O valor lido aparece no painel como `Luminosidade`.

Regra atual:

- quando a leitura ficar menor ou igual ao `limite de escuridao`
- e houver presenca no `PIR`
- o LED acende automaticamente
- ele permanece ligado pelo tempo configurado no painel

O usuario pode editar no app:

- ativar ou desativar a automacao
- limite de escuridao
- tempo de permanencia do LED ligado

## Pinagem do ESP32-CAM

O codigo foi preparado para o modelo `AI Thinker ESP32-CAM`, que e o mais comum.

## Como usar sem IP publico ou URL

Voce nao precisa de servidor externo para a atividade.

O "app" pode ser a propria pagina web hospedada no ESP32 principal.

No codigo atual, o `ESP32` esta configurado por padrao em `Access Point`, ou seja:

- ele cria uma rede Wi-Fi chamada `PetMonitorESP32`
- a senha e `pet12345`
- o celular conecta direto nessa rede
- o painel abre no endereco `http://192.168.4.1`

Isso resolve o problema de nao ter dominio, hospedagem ou backend.

## O que configurar no codigo

No `esp32_main.ino`, ajuste principalmente:

- `USE_SOFT_AP`: `true` para modo local, `false` para usar roteador
- `WIFI_SSID` e `WIFI_PASSWORD`: se usar roteador
- `AP_SSID` e `AP_PASSWORD`: nome e senha da rede criada pelo ESP32
- `cameraBaseUrl`: IP do `ESP32-CAM`, se quiser embutir a camera no painel
- pinos dos sensores: `DHT_PIN`, `TRIG_FOOD_PIN`, `ECHO_FOOD_PIN`, `PIR_PIN`, `GAS_PIN`, `LIGHT_SENSOR_PIN`, `SERVO_PIN`, `LED_PIN`
- limites gerais do sistema: temperatura, gas, nivel baixo e intervalo de alimentacao

No `esp32_cam.ino`, ajuste:

- `WIFI_SSID`
- `WIFI_PASSWORD`

## Como usar

1. Abra o projeto no PlatformIO.
2. Se quiser usar o modo local sem roteador, mantenha `USE_SOFT_AP = true` no `esp32_main.ino`.
3. Grave o `ESP32` principal.
4. No celular, conecte na rede `PetMonitorESP32`.
5. Abra `http://192.168.4.1` no navegador.

## Como usar com roteador Wi-Fi

1. Altere `USE_SOFT_AP` para `false` no `esp32_main.ino`.
2. Ajuste `WIFI_SSID` e `WIFI_PASSWORD`.
3. Grave o `ESP32` principal.
4. Veja o IP no monitor serial.
5. Acesse esse IP no navegador.

## Web app e futuro com Vercel

Hoje o projeto funciona como `web app local`:

- o proprio `ESP32` hospeda o painel
- o celular acessa pelo navegador
- os comandos vao direto para o `ESP32`

Voce pode apresentar isso como o "aplicativo" do sistema.

### O que o Vercel faria

O `Vercel` pode hospedar:

- a interface web
- um painel mais bonito
- funcoes web para integrar com banco ou API

### O que o Vercel nao resolve sozinho

O `ESP32` normalmente fica dentro da rede local, entao o Vercel sozinho nao consegue falar com ele diretamente pela internet.

Para controle remoto real, a arquitetura ideal seria:

1. `ESP32` conectado ao Wi-Fi
2. `ESP32` enviando dados para um backend
3. frontend hospedado no `Vercel`
4. usuario enviando comandos pelo frontend
5. backend entregando comandos para o `ESP32`

Para essa evolucao, os caminhos mais simples sao:

- `Firebase`
- `Supabase`
- `MQTT`
- backend proprio em `Render`, `Railway` ou similar

## Uso da camera

Para a camera aparecer dentro do painel principal, o `ESP32-CAM` precisa estar acessivel por IP na mesma rede do painel.

O caminho mais simples para a atividade e:

- primeiro apresentar o painel do `ESP32` principal em `http://192.168.4.1`
- depois abrir separadamente a pagina do `ESP32-CAM` quando ele estiver conectado a uma rede Wi-Fi comum

Se voce quiser, eu posso montar a proxima versao para o `ESP32-CAM` se conectar automaticamente na rede criada pelo ESP32 principal.

## Endpoints do ESP32 principal

- `GET /` painel web
- `GET /api/status` estado atual em JSON
- `POST /api/feed` libera racao manualmente
- `POST /api/led?state=on` liga LED
- `POST /api/led?state=off` desliga LED
- `POST /api/led?state=auto` devolve o LED ao modo automatico

## Endpoints do ESP32-CAM

- `GET /` pagina simples da camera
- `GET /capture` captura JPEG unica
- `GET /stream` stream MJPEG

## Webhook de notificacao

O projeto possui suporte opcional a webhook HTTP.

Exemplos:

- automacao no Make
- endpoint de backend proprio
- integracao com Telegram via API intermediaria

Se nao quiser usar agora, deixe `WEBHOOK_ENABLED` como `false`.

## Observacoes

- Para apresentacao academica, o painel web local do ESP32 ja pode ser considerado o "aplicativo" do sistema.
- O sensor de gas e lido de forma analogica. Para uso real, calibre conforme o modelo do sensor.
- O calculo do nivel de agua e racao foi feito por distancia ultrassonica. Ajuste as alturas dos recipientes.
- Para o servo, confira a fonte de alimentacao. Nao e recomendado alimentar servo diretamente da USB do ESP32 em carga real.
