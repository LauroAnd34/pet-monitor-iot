# Pet Guardian IoT

Projeto final de sistema IoT para monitoramento e cuidado remoto de pets, integrando firmware embarcado, dashboard web responsivo, backend em nuvem e aplicativo Android com visual personalizado.

## Visão geral

O Pet Guardian foi pensado para acompanhar alimentação, água, conforto ambiental, presença, iluminação e registros visuais do pet em uma experiência acessível e agradável de usar.

O sistema combina:

- `ESP32 principal` para leitura de sensores e acionamento de bomba, motor de ração e lâmpada
- `ESP32 + OV7670 sem FIFO` como nó dedicado de câmera para capturas futuras
- `Supabase` para ingestão de telemetria, comandos e histórico
- `Dashboard web/PWA` para acompanhamento remoto no navegador
- `App Android` com temas pastéis, histórico, controle, fotos e personalização do perfil do pet

## Funcionalidades implementadas

### Hub IoT

- leitura de temperatura e umidade
- leitura de luminosidade, gás e presença
- monitoramento de nível de ração e água
- controle de bomba d'água, motor de alimentação e luz
- base pronta para automações locais
- integração preparada para captura de fotos

### Aplicativo Android

- tela inicial com indicadores do sistema
- histórico com filtros e agrupamento de leituras
- área de fotos com álbuns e visualização ampliada
- tela de controle com comandos para os atuadores
- tela de ajustes com avatar, nome do pet, nome do ambiente e temas
- editor de avatar com recorte, zoom e pré-visualização
- temas visuais em tons pastéis

### Camada em nuvem

- schema SQL para dispositivos, telemetria e comandos
- edge functions do Supabase para ingestão e leitura do dashboard
- endpoint de controle remoto
- dashboard web com visual responsivo e configurável

## Estrutura do repositório

```text
pet_monitor_iot/
├── android_pet_guardian_app/
├── cloud/
│   ├── dashboard/
│   └── supabase/
├── src/
│   ├── esp32_pet_hub_dual/
│   └── esp32_ov7670_non_fifo_node/
├── platformio.ini
└── README.md
```

## Pastas principais

### `android_pet_guardian_app`
Aplicativo Android nativo do Pet Guardian.

### `cloud/dashboard`
Dashboard web/PWA para acompanhamento remoto.

### `cloud/supabase`
Schema SQL e edge functions para integração em nuvem.

### `src/esp32_pet_hub_dual`
Firmware principal do hub IoT com sensores e atuadores.

### `src/esp32_ov7670_non_fifo_node`
Nó de câmera usando ESP32 comum com OV7670 sem FIFO.

## Como executar

### 1. Firmware do hub principal

Abra o arquivo abaixo no Arduino IDE e ajuste Wi‑Fi, endpoints e pinos conforme o protótipo:

- `src/esp32_pet_hub_dual/esp32_pet_hub_dual.ino`

### 2. Firmware da câmera

Abra no Arduino IDE:

- `src/esp32_ov7670_non_fifo_node/esp32_ov7670_non_fifo_node.ino`

A documentação de apoio está em:

- `src/esp32_ov7670_non_fifo_node/README.md`

### 3. Dashboard web

Arquivos principais:

- `cloud/dashboard/index.html`
- `cloud/dashboard/styles.css`
- `cloud/dashboard/app.js`
- `cloud/dashboard/config.example.js`

Para publicar, a base já está preparada para Vercel.

### 4. Backend Supabase

Use o schema:

- `cloud/supabase/schema.sql`

E publique as functions:

- `cloud/supabase/functions/ingest-telemetry`
- `cloud/supabase/functions/dashboard-data`
- `cloud/supabase/functions/control-device`
- `cloud/supabase/functions/poll-command`

## Configuração local

Este repositório foi limpo para apresentação. Antes de rodar em ambiente real, configure seus valores locais em:

- `android_pet_guardian_app/app/src/main/java/com/lauro/petguardian/AppConfig.kt`
- `cloud/dashboard/config.js`

Use URLs, tokens e IPs do seu próprio ambiente.

## Tecnologias utilizadas

- `ESP32`
- `Arduino IDE`
- `Kotlin / Android`
- `Supabase`
- `HTML, CSS e JavaScript`
- `Vercel`

## Observações importantes

- O projeto foi organizado para apresentação final no GitHub.
- Arquivos temporários de build e credenciais locais foram separados da estrutura principal.
- A integração com câmera está pronta para evolução sem quebrar a arquitetura atual do app.

## Próximos passos recomendados

- integrar captura real da câmera ao fluxo do app
- consolidar autenticação de usuário
- adicionar push notifications reais
- concluir sincronização completa de comandos remotos com confirmação de execução
