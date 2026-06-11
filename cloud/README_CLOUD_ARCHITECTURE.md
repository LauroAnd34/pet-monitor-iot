# Pet Guardian Cloud

Essa camada tira o projeto do modo somente local e transforma o painel em um app web/PWA que pode ser aberto no celular e instalado na tela inicial.

## Arquitetura final

- `ESP32` coleta os dados do sistema IoT
- `ESP32` faz `POST` para uma edge function do `Supabase`
- `Supabase` grava as leituras e entrega um endpoint seguro para o app
- `cloud/dashboard` e um PWA estatico que pode ser publicado no `Vercel`, `Netlify` ou `Render`
- `Pico W / BitDogLab` continua como lampada local controlada pelo `ESP32`

## Pastas importantes

- `cloud/supabase/schema.sql`
- `cloud/supabase/functions/ingest-telemetry/index.ts`
- `cloud/supabase/functions/dashboard-data/index.ts`
- `cloud/dashboard/index.html`
- `cloud/dashboard/styles.css`
- `cloud/dashboard/app.js`
- `cloud/dashboard/config.example.js`
- `src/esp32_pet_hub_dual/esp32_pet_hub_dual.ino`

## O que foi feito

### Firmware ESP32

O firmware principal agora ficou pronto para:

- conectar no Wi-Fi da casa
- continuar funcionando localmente se voce quiser voltar para `Soft AP`
- enviar telemetria para a nuvem em intervalo fixo
- manter o painel local como fallback
- mostrar estado da nuvem tambem no dashboard local

### Backend Supabase

Foram preparados:

- tabela de dispositivos
- tabela de eventos de telemetria
- tabela de comandos para evolucao futura
- view com o snapshot mais recente
- edge function `ingest-telemetry` para receber dados do `ESP32`
- edge function `dashboard-data` para o app consumir so com `dashboard_token`

### App web / PWA

O app em `cloud/dashboard` foi desenhado para:

- abrir bem no celular
- parecer um produto final, nao um painel improvisado
- mostrar conforto, racao, agua, ambiente, atuadores e historico
- funcionar como PWA
- oferecer temas pasteis selecionaveis pelo usuario e persistidos no navegador
- ter modo demo enquanto a nuvem nao esta configurada

## Como colocar no ar

### 1. Criar projeto no Supabase

No seu projeto Supabase:

1. crie um novo projeto
2. rode o SQL de `cloud/supabase/schema.sql`
3. publique as edge functions:
   - `ingest-telemetry`
   - `dashboard-data`

Voce vai precisar destas variaveis nas functions:

- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`

## 2. Criar um dispositivo

Depois do schema aplicado, crie um dispositivo manualmente:

```sql
insert into public.devices (name, hardware_type, token)
values (
  'Pet Hub Casa',
  'esp32 + pico w',
  'COLOQUE_AQUI_UM_TOKEN_SECRETO_DO_ESP32'
)
returning id, name, token, dashboard_token;
```

Guarde dois valores:

- `token`: vai para o `ESP32`
- `dashboard_token`: vai para o app

## 3. Configurar o ESP32

No arquivo `src/esp32_pet_hub_dual/esp32_pet_hub_dual.ino`, ajuste:

```cpp
const bool USE_SOFT_AP = false;
const char* WIFI_SSID = "SEU_WIFI";
const char* WIFI_PASSWORD = "SUA_SENHA";
const bool CLOUD_SYNC_ENABLED = true;
const char* CLOUD_INGEST_URL = "https://SEU-PROJETO.supabase.co/functions/v1/ingest-telemetry";
const char* CLOUD_DEVICE_TOKEN = "TOKEN_DO_ESP32";
```

Se quiser voltar para modo local temporariamente:

```cpp
const bool USE_SOFT_AP = true;
```

## 4. Configurar o dashboard

Copie `cloud/dashboard/config.example.js` para `cloud/dashboard/config.js` e preencha:

```js
window.PET_DASHBOARD_CONFIG = {
  apiBaseUrl: "https://SEU-PROJETO.supabase.co/functions/v1/dashboard-data",
  dashboardToken: "TOKEN_PUBLICO_DO_APP",
  refreshIntervalMs: 15000,
  demoMode: false,
  defaultTheme: 'blossom'
};
```

## 5. Publicar no Vercel

A forma mais simples:

1. suba a pasta `cloud/dashboard` para um repositorio
2. importe esse diretorio no `Vercel`
3. publique como site estatico

O arquivo `vercel.json` ja foi deixado pronto para esse caso.

Se quiser, `Netlify` tambem funciona muito bem para essa pasta.

## Fluxo final

1. `ESP32` envia os dados para `ingest-telemetry`
2. `Supabase` salva tudo em `telemetry_events`
3. o app chama `dashboard-data`
4. o usuario ve o sistema no celular em tempo real

## Fluxo de fotos da OV7670

A ESP32 comum com OV7670 sem FIFO usa uma fila separada da ESP32 dos
sensores. Assim, o hub de sensores nunca consome pedidos destinados a camera.

1. O app envia `capture_photo` para `control-device`.
2. A ESP32 da OV7670 consulta `poll-camera-command` com `x-device-token`.
3. A camera captura o BMP e envia os bytes para `upload-photo`.
4. O Supabase grava o arquivo no bucket privado `pet-photos` e os metadados
   na tabela `pet_photos`.
5. O app consulta `photos` com `x-dashboard-token` e recebe URLs assinadas
   validas por 15 minutos.

### Endpoints da camera

```text
GET  /functions/v1/poll-camera-command
POST /functions/v1/upload-photo
```

Cabecalhos usados pela camera:

```text
x-device-token: TOKEN_DO_ESP32
x-command-id: ID_RECEBIDO_NO_POLL
x-photo-reason: manual
Content-Type: image/bmp
```

O corpo do `POST upload-photo` deve conter diretamente os bytes do arquivo
BMP. O limite atual e `300000` bytes.

### Endpoint do app

```text
GET /functions/v1/photos?limit=40
x-dashboard-token: TOKEN_PUBLICO_DO_APP
```

O bucket e privado. O app recebe uma `downloadUrl` assinada em vez de acesso
publico permanente aos arquivos.

## Observacao importante

Nesta etapa, o app esta focado em acompanhamento remoto. O caminho para comandos remotos completos existe, mas ainda faltaria o `ESP32` buscar comandos pendentes da nuvem para executar bomba, comedouro ou lampada fora da rede local.

Ou seja:

- monitoramento remoto: pronto para avancar
- automacao local: pronta
- atuacao remota pela nuvem: arquitetura preparada, mas nao fechada ainda
