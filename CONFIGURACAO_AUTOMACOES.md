# Configuracao das automacoes

## App Android

Edite `android_pet_guardian_app/app/src/main/java/com/lauro/petguardian/AppConfig.kt`:

- `DASHBOARD_API_URL`: URL da funcao `dashboard-data`.
- `COMMAND_API_URL`: URL da funcao `control-device`.
- `DASHBOARD_TOKEN`: token privado do dashboard.
- `CAMERA_NODE_URL`: endereco local da ESP32 da camera, por exemplo `http://192.168.1.50`.

Ao abrir o app pela primeira vez, permita notificacoes. O Android executa o monitor em segundo plano
periodicamente. Com o app aberto, movimento e temperatura sao verificados com mais frequencia.

## Hub ESP32

Edite `src/esp32_pet_hub_dual/esp32_pet_hub_dual.ino`:

- `CAMERA_CAPTURE_URL`: endereco completo da captura da camera, por exemplo
  `http://192.168.1.50/capture.bmp?reason=motion`.

O hub chama essa URL quando o PIR muda de sem movimento para movimento. Existe um intervalo de cinco
minutos entre capturas para evitar excesso de fotos.

## Recursos no app

- Fotos automaticas por movimento e temperatura.
- Notificacoes Android com foto.
- Avisos de hub, sensores e camera offline.
- Limpeza automatica configuravel em 7, 30, 90 ou 365 dias.
- Linha do tempo combinando fotos, alertas, alimentacao e agua.
- Comparacao entre hoje e ontem em horario semelhante.
- Compartilhamento pelo menu padrao do Android, incluindo WhatsApp.
