#include "esp_camera.h"
#include <WiFi.h>
#include <WebServer.h>

// No separado da camera para o Pet Guardian.
// Pensado para uma placa ESP32-CAM AI Thinker dedicada so para capturas.

const char* WIFI_SSID = "Lauroo";
const char* WIFI_PASSWORD = "Lalalauro23";

const char* CAMERA_NODE_NAME = "pet-camera-node";
const char* CAMERA_CAPTURE_REASON_DEFAULT = "manual";

WebServer server(80);

// AI Thinker ESP32-CAM
#define PWDN_GPIO_NUM 32
#define RESET_GPIO_NUM -1
#define XCLK_GPIO_NUM 0
#define SIOD_GPIO_NUM 26
#define SIOC_GPIO_NUM 27

#define Y9_GPIO_NUM 35
#define Y8_GPIO_NUM 34
#define Y7_GPIO_NUM 39
#define Y6_GPIO_NUM 36
#define Y5_GPIO_NUM 21
#define Y4_GPIO_NUM 19
#define Y3_GPIO_NUM 18
#define Y2_GPIO_NUM 5
#define VSYNC_GPIO_NUM 25
#define HREF_GPIO_NUM 23
#define PCLK_GPIO_NUM 22

unsigned long captureCount = 0;
unsigned long lastCaptureAtMs = 0;
String lastCaptureReason = "nenhuma";

bool setupCamera() {
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  config.frame_size = FRAMESIZE_VGA;
  config.jpeg_quality = 14;
  config.fb_count = 1;
  config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.print("[CAM] Erro ao iniciar camera: 0x");
    Serial.println(err, HEX);
    return false;
  }

  sensor_t* sensor = esp_camera_sensor_get();
  sensor->set_framesize(sensor, FRAMESIZE_VGA);
  sensor->set_brightness(sensor, 0);
  sensor->set_contrast(sensor, 0);
  sensor->set_saturation(sensor, 0);
  sensor->set_vflip(sensor, 0);
  sensor->set_hmirror(sensor, 0);
  return true;
}

void connectWiFi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  Serial.print("[WIFI] Conectando em ");
  Serial.println(WIFI_SSID);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println();
  Serial.print("[WIFI] IP da camera: http://");
  Serial.println(WiFi.localIP());
}

void sendCameraFrame(camera_fb_t* fb) {
  WiFiClient client = server.client();
  client.print("HTTP/1.1 200 OK\r\n");
  client.print("Content-Type: image/jpeg\r\n");
  client.print("Content-Length: " + String(fb->len) + "\r\n");
  client.print("Cache-Control: no-store, no-cache, must-revalidate, max-age=0\r\n");
  client.print("Connection: close\r\n\r\n");
  client.write(fb->buf, fb->len);
}

void handleRoot() {
  String html = R"rawliteral(
<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Pet Guardian Camera</title>
  <style>
    body {
      margin: 0;
      padding: 24px;
      font-family: Arial, sans-serif;
      background: #fff6f8;
      color: #4f3141;
      text-align: center;
    }
    .card {
      max-width: 760px;
      margin: 0 auto;
      padding: 20px;
      background: #fffdf8;
      border: 1px solid #f0dbe2;
      border-radius: 24px;
      box-shadow: 0 18px 36px rgba(118, 74, 96, 0.08);
    }
    img {
      width: min(100%, 680px);
      border-radius: 18px;
      border: 2px solid #f0dbe2;
      background: #f6eef2;
    }
    a {
      color: #d85d8b;
      font-weight: bold;
      text-decoration: none;
    }
    .actions {
      display: flex;
      gap: 12px;
      justify-content: center;
      flex-wrap: wrap;
      margin-top: 18px;
    }
  </style>
</head>
<body>
  <div class="card">
    <h1>Camera do Pet Guardian</h1>
    <p>Este no responde sozinho pelas fotos do sistema.</p>
    <img src="/capture?reason=preview" alt="Foto atual da camera">
    <div class="actions">
      <a href="/capture?reason=manual" target="_blank">Tirar foto agora</a>
      <a href="/status" target="_blank">Ver status JSON</a>
    </div>
  </div>
</body>
</html>
)rawliteral";

  server.send(200, "text/html; charset=utf-8", html);
}

void handleCapture() {
  camera_fb_t* fb = esp_camera_fb_get();
  if (!fb) {
    server.send(500, "application/json", "{\"ok\":false,\"error\":\"Falha ao capturar imagem\"}");
    return;
  }

  lastCaptureReason = server.hasArg("reason") ? server.arg("reason") : CAMERA_CAPTURE_REASON_DEFAULT;
  captureCount++;
  lastCaptureAtMs = millis();

  Serial.print("[CAM] Captura #");
  Serial.print(captureCount);
  Serial.print(" motivo: ");
  Serial.println(lastCaptureReason);

  sendCameraFrame(fb);
  esp_camera_fb_return(fb);
}

void handleStatus() {
  String json = "{";
  json += "\"ok\":true,";
  json += "\"node\":\"" + String(CAMERA_NODE_NAME) + "\",";
  json += "\"ip\":\"" + WiFi.localIP().toString() + "\",";
  json += "\"captures\":" + String(captureCount) + ",";
  json += "\"lastCaptureAtMs\":" + String(lastCaptureAtMs) + ",";
  json += "\"lastCaptureReason\":\"" + lastCaptureReason + "\",";
  json += "\"signal\":" + String(WiFi.RSSI());
  json += "}";
  server.send(200, "application/json", json);
}

void handlePhotoMeta() {
  String json = "{";
  json += "\"ok\":true,";
  json += "\"captureUrl\":\"http://" + WiFi.localIP().toString() + "/capture?reason=app\",";
  json += "\"lastCaptureReason\":\"" + lastCaptureReason + "\",";
  json += "\"captures\":" + String(captureCount);
  json += "}";
  server.send(200, "application/json", json);
}

void setup() {
  Serial.begin(115200);
  delay(1500);
  Serial.println();
  Serial.println("=== Pet Guardian Camera Node ===");

  if (!setupCamera()) {
    Serial.println("[CAM] Camera nao iniciou. Reinicie a placa.");
    while (true) {
      delay(1000);
    }
  }

  connectWiFi();

  server.on("/", HTTP_GET, handleRoot);
  server.on("/capture", HTTP_GET, handleCapture);
  server.on("/status", HTTP_GET, handleStatus);
  server.on("/photo-meta", HTTP_GET, handlePhotoMeta);
  server.begin();

  Serial.println("[HTTP] Rotas prontas:");
  Serial.println("[HTTP] /");
  Serial.println("[HTTP] /capture?reason=manual");
  Serial.println("[HTTP] /status");
  Serial.println("[HTTP] /photo-meta");
  Serial.println("[NEXT] Futuramente o app pode chamar /photo-meta e depois buscar /capture.");
}

void loop() {
  server.handleClient();
}
