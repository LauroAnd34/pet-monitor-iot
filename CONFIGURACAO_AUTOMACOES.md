# Configuracao das automacoes

## App Android

Edite `android_pet_guardian_app/app/src/main/java/com/lauro/petguardian/AppConfig.kt`:

- `DASHBOARD_API_URL`: URL da funcao `dashboard-data`.
- `COMMAND_API_URL`: URL da funcao `control-device`.
- `PHOTOS_API_URL`: URL da funcao `photos`.
- `DASHBOARD_TOKEN`: token privado do dashboard.
Ao abrir o app pela primeira vez, permita notificacoes. O Android executa o monitor em segundo plano
periodicamente. Com o app aberto, movimento e temperatura sao verificados com mais frequencia.

## Hub ESP32

O hub de sensores nao precisa conhecer o IP da camera. A camera publica diretamente na nuvem e no app.

## ESP32 com OV7670

Configure no sketch:

- Wi-Fi local.
- `CLOUD_DEVICE_TOKEN`.
- URLs das funcoes `poll-camera-command` e `upload-photo`.

## Recursos no app

- Fotos automaticas por movimento e temperatura.
- Notificacoes Android com foto.
- Avisos de hub, sensores e camera offline.
- Limpeza automatica configuravel em 7, 30, 90 ou 365 dias.
- Linha do tempo combinando fotos, alertas, alimentacao e agua.
- Comparacao entre hoje e ontem em horario semelhante.
- Compartilhamento pelo menu padrao do Android, incluindo WhatsApp.
