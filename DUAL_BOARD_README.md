# Pet Guardian Hub - ESP32 + Pico W BitDogLab

Arquitetura reformulada para duas placas:

- `ESP32`: hub principal com sensores, bomba, motor DC do comedouro, automacao e web app
- `Pico W / BitDogLab`: lampada visual usando a matriz de LED

## Divisao recomendada dos sensores

Para este projeto, a melhor divisao pratica e:

- `ESP32`: `PIR`, `DHT11`, `sensor de gas`, `LDR`, `2 ultrassonicos`, `motor DC`, `bomba`
- `Pico W / BitDogLab`: matriz de LED

Motivo:

- o `ESP32` lida melhor com varios sensores, saidas, Wi-Fi e painel web ao mesmo tempo
- a `BitDogLab` brilha mais como modulo dedicado para a matriz de LED
- manter os sensores todos no `ESP32` simplifica muito a logica e a manutencao

Se voce quiser muito deslocar um sensor para a BitDogLab, o mais plausivel seria o `LDR`, mas eu nao recomendo nessa primeira versao porque complica a sincronizacao sem trazer grande ganho.

## Comunicacao entre placas

As placas conversam da forma mais simples para esse caso: `1 pino digital + GND comum`.

### Ligacao sugerida

- `ESP32 GPIO 13` -> `Pico W GP9`
- `GND` do `ESP32` -> `GND` do `Pico W`

Esse sinal trabalha em `3.3V`. Nao ligue `5V` diretamente em GPIO do Pico W.

## Alimentacao

Voce comentou que vai usar o `HW-131` para energia externa. A recomendacao e:

- motor DC do comedouro e bomba com alimentacao externa
- `GND` comum entre fonte, `ESP32`, rele/modulo da bomba e `Pico W`

Isso evita queda de tensao e reset aleatorio.

## Pinagem do ESP32

- `DHT11`: `GPIO 4`
- `Ultrassonico racao`: `TRIG 18`, `ECHO 19`
- `Ultrassonico agua`: `TRIG 5`, `ECHO 17`
- `PIR`: `GPIO 27`
- `Sensor de gas analogico`: `GPIO 34`
- `LDR analogico`: `GPIO 35`
- `Motor DC do comedouro`: `GPIO 14`
- `Bomba / rele`: `GPIO 26`
- `Sinal da lampada BitDogLab`: `GPIO 13`

## Arquivos principais

- [`src/esp32_pet_hub_dual/esp32_pet_hub_dual.ino`](C:\Users\fakef\OneDrive\Documentos\New project\pet_monitor_iot\src\esp32_pet_hub_dual\esp32_pet_hub_dual.ino)
- [`src/pico_w_bitdoglab/bitdoglab_pet_lamp.py`](C:\Users\fakef\OneDrive\Documentos\New project\pet_monitor_iot\src\pico_w_bitdoglab\bitdoglab_pet_lamp.py)

## O que o sistema faz

### ESP32

- hospeda um web app responsivo
- monitora temperatura, umidade, gas, luminosidade, presenca, nivel de agua e racao
- libera racao via motor DC
- aciona bomba de agua
- aplica regras de automacao
- coordena a lampada da BitDogLab
- ja deixa a arquitetura pronta para futura integracao com `ESP32-CAM`

### Pico W BitDogLab

- recebe um sinal digital do `ESP32`
- controla a matriz como lampada branca
- `HIGH` liga a matriz
- `LOW` desliga a matriz

## Web app

O painel web roda no proprio `ESP32`. Ele foi pensado para ser:

- responsivo
- acessivel
- visualmente mais sofisticado
- facil de entender em apresentacao

Ele mostra:

- ambiente
- racao e agua
- bomba
- estado da lampada BitDogLab
- configuracoes de automacao
- area pronta para integracao futura da camera

## Regras automaticas

### Lampada BitDogLab

- se estiver escuro
- e houver presenca
- a matriz acende automaticamente
- o usuario pode configurar brilho, limiar de escuridao e tempo ligado

### Alimentacao

- o motor DC pode liberar racao manualmente
- ou em intervalo automatico configuravel

### Agua

- a bomba pode ser acionada manualmente
- ou automaticamente quando o nivel de agua ficar abaixo do limite configurado

## Integracao futura com ESP32-CAM

Esta versao nao depende da camera, mas a arquitetura ja reserva esse crescimento.

Quando a `ESP32-CAM` voltar para o projeto, a recomendacao e:

- manter o `ESP32` como hub principal
- deixar a `ESP32-CAM` so para video
- embutir o stream no painel existente

## Observacoes importantes

- A BitDogLab pode usar `GP9` como entrada do sinal da lampada. Ajuste se voce escolher outro pino.
- O `DHT11` foi configurado por padrao. Se voce trocar por outro modelo, ajuste no firmware do `ESP32`.
- O controle do motor DC e da bomba foi pensado para uso com rele, transistor, MOSFET, ponte H ou modulo adequado. Nao ligue carga maior direto no GPIO.
- Eu nao consegui compilar nem gravar daqui, entao a validacao final precisa ser feita nas placas.
