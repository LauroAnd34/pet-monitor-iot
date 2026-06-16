#include <WiFi.h>
#include <WiFiClient.h>
#include <HTTPClient.h>
#include "OV7670.h"

// Configure localmente antes de gravar. Nao publique credenciais reais.
const char* WIFI_SSID = "SEU_WIFI";
const char* WIFI_PASSWORD = "SUA_SENHA";
const char* CLOUD_DEVICE_TOKEN = "SEU_DEVICE_TOKEN";
const char* CAMERA_COMMAND_URL = "https://SEU_PROJETO.supabase.co/functions/v1/poll-camera-command";
const char* PHOTO_UPLOAD_URL = "https://SEU_PROJETO.supabase.co/functions/v1/upload-photo";
constexpr unsigned long COMMAND_POLL_INTERVAL_MS = 4000;
constexpr unsigned long WIFI_RETRY_INTERVAL_MS = 10000;

#define CAM_WIDTH 80
#define CAM_HEIGHT 60

constexpr int SIOD_PIN = 21;
constexpr int SIOC_PIN = 22;
constexpr int VSYNC_PIN = 34;
constexpr int HREF_PIN = 35;
constexpr int XCLK_PIN = 32;
constexpr int PCLK_PIN = 33;
constexpr int D0_PIN = 27;
constexpr int D1_PIN = 5;
constexpr int D2_PIN = 2;
constexpr int D3_PIN = 15;
constexpr int D4_PIN = 14;
constexpr int D5_PIN = 13;
constexpr int D6_PIN = 12;
constexpr int D7_PIN = 4;

constexpr int CROP_LEFT = 4;
constexpr int CROP_RIGHT = 4;
constexpr int CROP_TOP = 5;
constexpr int CROP_BOTTOM = 6;
constexpr int OUT_WIDTH = CAM_WIDTH - CROP_LEFT - CROP_RIGHT;
constexpr int OUT_HEIGHT = CAM_HEIGHT - CROP_TOP - CROP_BOTTOM;

OV7670* camera = nullptr;
WiFiServer server(80);
constexpr size_t BMP_HEADER_SIZE = 54;
constexpr size_t BMP_ROW_SIZE = ((OUT_WIDTH * 3) + 3) & ~3;
constexpr size_t BMP_SIZE = BMP_HEADER_SIZE + (BMP_ROW_SIZE * OUT_HEIGHT);
unsigned char bmpHeader[BMP_HEADER_SIZE];
uint8_t bmpRowBuffer[BMP_ROW_SIZE];

unsigned long frameCount = 0;
unsigned long lastFrameMs = 0;
unsigned long lastCommandPollMs = 0;
unsigned long lastWifiRetryMs = 0;
String lastReason = "idle";
String lastCloudStatus = "idle";

void writeLittleEndian32(unsigned char* buffer, int offset, uint32_t value) {
  buffer[offset + 0] = value & 0xFF;
  buffer[offset + 1] = (value >> 8) & 0xFF;
  buffer[offset + 2] = (value >> 16) & 0xFF;
  buffer[offset + 3] = (value >> 24) & 0xFF;
}

void writeLittleEndian16(unsigned char* buffer, int offset, uint16_t value) {
  buffer[offset + 0] = value & 0xFF;
  buffer[offset + 1] = (value >> 8) & 0xFF;
}

void constructBmpHeader(unsigned char* header, uint32_t width, uint32_t height) {
  const uint32_t rowSize = ((width * 3) + 3) & ~3;
  const uint32_t pixelDataSize = rowSize * height;
  const uint32_t fileSize = BMP_HEADER_SIZE + pixelDataSize;

  memset(header, 0, BMP_HEADER_SIZE);
  header[0] = 'B';
  header[1] = 'M';
  writeLittleEndian32(header, 2, fileSize);
  writeLittleEndian32(header, 10, BMP_HEADER_SIZE);
  writeLittleEndian32(header, 14, 40);
  writeLittleEndian32(header, 18, width);
  writeLittleEndian32(header, 22, height);
  writeLittleEndian16(header, 26, 1);
  writeLittleEndian16(header, 28, 24);
  writeLittleEndian32(header, 34, pixelDataSize);
  writeLittleEndian32(header, 38, 2835);
  writeLittleEndian32(header, 42, 2835);
}

void rgb565ToSoftColor(uint16_t pixel, uint8_t& outR, uint8_t& outG, uint8_t& outB) {
  uint8_t r = ((pixel >> 11) & 0x1F) << 3;
  uint8_t g = ((pixel >> 5) & 0x3F) << 2;
  uint8_t b = (pixel & 0x1F) << 3;

  uint16_t gray = (r * 30 + g * 45 + b * 25) / 100;
  if (gray < 28) gray = 28;
  if (gray > 225) gray = 225;

  outR = static_cast<uint8_t>((gray * 55 + r * 45) / 100);
  outG = static_cast<uint8_t>((gray * 62 + g * 38) / 100);
  outB = static_cast<uint8_t>((gray * 58 + b * 42) / 100);

  if (outG > outR + 28) outG = outR + 28;
  if (outG > outB + 28) outG = outB + 28;
}

void buildClarityBmpRow(int sourceRowIndex) {
  memset(bmpRowBuffer, 0, BMP_ROW_SIZE);
  const uint16_t* srcRow = reinterpret_cast<const uint16_t*>(camera->frame + (sourceRowIndex * CAM_WIDTH * 2));

  for (int x = 0; x < OUT_WIDTH; ++x) {
    uint16_t pixel = srcRow[x + CROP_LEFT];
    uint8_t r = 0;
    uint8_t g = 0;
    uint8_t b = 0;
    rgb565ToSoftColor(pixel, r, g, b);

    size_t dst = x * 3;
    bmpRowBuffer[dst + 0] = b;
    bmpRowBuffer[dst + 1] = g;
    bmpRowBuffer[dst + 2] = r;
  }
}

void sendHtmlPage(WiFiClient& client) {
  client.println("HTTP/1.1 200 OK");
  client.println("Content-Type: text/html; charset=utf-8");
  client.println("Connection: close");
  client.println();
  client.println("<!DOCTYPE html>");
  client.println("<html lang='pt-BR'>");
  client.println("<head>");
  client.println("<meta charset='UTF-8'>");
  client.println("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
  client.println("<title>Pet Guardian OV7670</title>");
  client.println("<style>");
  client.println("body{margin:0;padding:24px;font-family:Arial,sans-serif;background:#fff6f8;color:#4f3141;text-align:center;}");
  client.println(".card{max-width:760px;margin:0 auto;padding:20px;background:#fffdf8;border:1px solid #f0dbe2;border-radius:24px;box-shadow:0 18px 36px rgba(118,74,96,.08);}");
  client.println("img{width:min(100%,680px);border-radius:18px;border:2px solid #f0dbe2;background:#f6eef2;image-rendering:pixelated;}");
  client.println("a{color:#d85d8b;font-weight:bold;text-decoration:none;margin:0 10px;}");
  client.println("small{display:block;margin-top:12px;color:#8b6878;} ");
  client.println("</style>");
  client.println("</head>");
  client.println("<body>");
  client.println("<div class='card'>");
  client.println("<h1>Camera OV7670 do Pet Guardian</h1>");
  client.println("<p>ESP32 comum com OV7670 sem FIFO.</p>");
  client.println("<img src='/capture.bmp?reason=preview' alt='Foto atual da camera'>");
  client.println("<p><a href='/capture.bmp?reason=manual' target='_blank'>Tirar foto agora</a><a href='/status' target='_blank'>Ver status</a></p>");
  client.println("<small>Modo clareza colorido: imagem cortada nas bordas e com cor suavizada para reduzir o ruido visual.</small>");
  client.println("</div>");
  client.println("</body>");
  client.println("</html>");
}

void sendStatus(WiFiClient& client) {
  client.println("HTTP/1.1 200 OK");
  client.println("Content-Type: application/json");
  client.println("Connection: close");
  client.println();
  client.print("{");
  client.print("\"ok\":true,");
  client.print("\"ip\":\"");
  client.print(WiFi.localIP());
  client.print("\",");
  client.print("\"frames\":");
  client.print(frameCount);
  client.print(",\"lastFrameMs\":");
  client.print(lastFrameMs);
  client.print(",\"lastReason\":\"");
  client.print(lastReason);
  client.print("\",");
  client.print("\"width\":");
  client.print(OUT_WIDTH);
  client.print(",\"height\":");
  client.print(OUT_HEIGHT);
  client.print(",\"cloudStatus\":\"");
  client.print(lastCloudStatus);
  client.print("\"");
  client.println("}");
}

void sendCapture(WiFiClient& client, const String& reason) {
  if (!camera || !camera->vsyncOk) {
    client.println("HTTP/1.1 503 Service Unavailable");
    client.println("Content-Type: application/json");
    client.println("Connection: close");
    client.println();
    client.println("{\"ok\":false,\"error\":\"camera_not_ready\"}");
    return;
  }

  lastReason = reason;
  camera->oneFrame();
  frameCount++;
  lastFrameMs = millis();

  client.println("HTTP/1.1 200 OK");
  client.println("Content-Type: image/bmp");
  client.println("Cache-Control: no-store, no-cache, must-revalidate, max-age=0");
  client.println("Connection: close");
  client.println();

  client.write(bmpHeader, BMP_HEADER_SIZE);
  for (int row = CAM_HEIGHT - CROP_BOTTOM - 1; row >= CROP_TOP; --row) {
    buildClarityBmpRow(row);
    client.write(bmpRowBuffer, BMP_ROW_SIZE);
  }
}

String readRequestLine(WiFiClient& client) {
  String requestLine = client.readStringUntil('\r');
  client.readStringUntil('\n');
  return requestLine;
}

String parseReason(const String& requestLine) {
  int reasonIndex = requestLine.indexOf("reason=");
  if (reasonIndex < 0) return "manual";
  String reason = requestLine.substring(reasonIndex + 7);
  int spaceIndex = reason.indexOf(' ');
  if (spaceIndex >= 0) {
    reason = reason.substring(0, spaceIndex);
  }
  return reason;
}

String extractJsonString(const String& payload, const String& key) {
  String needle = "\"" + key + "\":\"";
  int start = payload.indexOf(needle);
  if (start < 0) return "";
  start += needle.length();
  int end = payload.indexOf('"', start);
  return end < 0 ? "" : payload.substring(start, end);
}

long extractJsonLong(const String& payload, const String& key) {
  String needle = "\"" + key + "\":";
  int start = payload.indexOf(needle);
  if (start < 0) return 0;
  start += needle.length();
  while (start < payload.length() && payload[start] == ' ') start++;
  int end = start;
  while (end < payload.length() && isDigit(payload[end])) end++;
  return payload.substring(start, end).toInt();
}

bool captureBmp(uint8_t* destination) {
  if (!camera || !camera->vsyncOk || !destination) return false;
  camera->oneFrame();
  frameCount++;
  lastFrameMs = millis();

  // O frame sai em RGB565; aqui ele vira BMP 24 bits para abrir facil no app/navegador.
  memcpy(destination, bmpHeader, BMP_HEADER_SIZE);
  size_t offset = BMP_HEADER_SIZE;
  for (int row = CAM_HEIGHT - CROP_BOTTOM - 1; row >= CROP_TOP; --row) {
    buildClarityBmpRow(row);
    memcpy(destination + offset, bmpRowBuffer, BMP_ROW_SIZE);
    offset += BMP_ROW_SIZE;
  }
  return true;
}

bool uploadCloudPhoto(long commandId, const String& reason) {
  // A imagem compacta e montada temporariamente para caber junto aos buffers TLS.
  uint8_t* bmp = static_cast<uint8_t*>(malloc(BMP_SIZE));
  if (!bmp) {
    lastCloudStatus = "sem_memoria";
    Serial.println("[CLOUD] Sem memoria para montar BMP.");
    return false;
  }

  if (!captureBmp(bmp)) {
    free(bmp);
    lastCloudStatus = "camera_indisponivel";
    return false;
  }

  HTTPClient http;
  http.begin(PHOTO_UPLOAD_URL);
  http.setConnectTimeout(20000);
  http.setTimeout(45000);
  http.addHeader("Content-Type", "image/bmp");
  http.addHeader("x-device-token", CLOUD_DEVICE_TOKEN);
  http.addHeader("x-command-id", String(commandId));
  http.addHeader("x-photo-reason", reason);

  // O comando segue no cabecalho para o backend marcar a solicitacao como concluida.
  Serial.println("[CLOUD] Enviando BMP de " + String(BMP_SIZE) + " bytes. Heap livre: " + String(ESP.getFreeHeap()));
  int httpCode = http.POST(bmp, BMP_SIZE);
  String response = http.getString();
  http.end();
  free(bmp);

  if (httpCode >= 200 && httpCode < 300) {
    lastCloudStatus = "foto_enviada";
    Serial.println("[CLOUD] Foto enviada. Comando: " + String(commandId));
    return true;
  }

  lastCloudStatus = "upload_http_" + String(httpCode);
  Serial.println("[CLOUD] Falha no upload HTTP " + String(httpCode) + ": " + response);
  return false;
}

void pollCameraCommand() {
  // A camera usa uma fila exclusiva para nao disputar comandos com o hub de sensores.
  if (WiFi.status() != WL_CONNECTED) return;

  HTTPClient http;
  http.begin(CAMERA_COMMAND_URL);
  http.setConnectTimeout(15000);
  http.setTimeout(20000);
  http.addHeader("x-device-token", CLOUD_DEVICE_TOKEN);
  int httpCode = http.GET();
  String response = http.getString();
  http.end();

  if (httpCode < 200 || httpCode >= 300) {
    lastCloudStatus = "poll_http_" + String(httpCode);
    Serial.println("[CLOUD] Falha ao consultar fila HTTP " + String(httpCode));
    return;
  }

  String commandType = extractJsonString(response, "commandType");
  if (commandType != "capture_photo") {
    lastCloudStatus = "fila_vazia";
    return;
  }

  // Somente comandos de foto chegam neste firmware; comandos do hub sao filtrados no backend.
  long commandId = extractJsonLong(response, "commandId");
  String reason = extractJsonString(response, "reason");
  if (reason.length() == 0) reason = "manual";
  Serial.println("[CLOUD] Pedido de foto recebido. Comando: " + String(commandId));
  uploadCloudPhoto(commandId, reason);
}

void maintainWifi() {
  if (WiFi.status() == WL_CONNECTED) return;
  unsigned long now = millis();
  if (now - lastWifiRetryMs < WIFI_RETRY_INTERVAL_MS) return;
  lastWifiRetryMs = now;

  // Mantem a camera tentando voltar para a nuvem sem reiniciar a placa inteira.
  Serial.println("[WIFI] Reconectando...");
  WiFi.disconnect();
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
}

void serve() {
  WiFiClient client = server.available();
  if (!client) {
    return;
  }

  unsigned long timeoutStart = millis();
  while (!client.available() && millis() - timeoutStart < 1200) {
    delay(1);
  }

  if (!client.available()) {
    client.stop();
    return;
  }

  String requestLine = readRequestLine(client);
  while (client.available()) {
    String headerLine = client.readStringUntil('\n');
    if (headerLine == "\r" || headerLine.length() <= 1) {
      break;
    }
  }

  Serial.print("[HTTP] ");
  Serial.println(requestLine);

  if (requestLine.startsWith("GET /status")) {
    sendStatus(client);
  } else if (requestLine.startsWith("GET /capture.bmp")) {
    sendCapture(client, parseReason(requestLine));
  } else {
    sendHtmlPage(client);
  }

  delay(1);
  client.stop();
}

void connectWifi() {
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  Serial.print("[WIFI] Conectando em ");
  Serial.println(WIFI_SSID);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print('.');
  }

  Serial.println();
  Serial.println("[WIFI] Conectado.");
  Serial.print("[WIFI] IP: ");
  Serial.println(WiFi.localIP());
}

void setupCameraNode() {
  camera = new OV7670(
    OV7670::QQQVGA_RGB565,
    SIOD_PIN, SIOC_PIN, VSYNC_PIN, HREF_PIN, XCLK_PIN, PCLK_PIN,
    D0_PIN, D1_PIN, D2_PIN, D3_PIN, D4_PIN, D5_PIN, D6_PIN, D7_PIN
  );
  constructBmpHeader(bmpHeader, OUT_WIDTH, OUT_HEIGHT);
  Serial.printf("[CAM] VSYNC: %s\n", camera->vsyncOk ? "OK" : "falhou");
  Serial.printf("[CAM] Resolucao bruta = %dx%d\n", CAM_WIDTH, CAM_HEIGHT);
  Serial.printf("[CAM] Resolucao exibida = %dx%d\n", OUT_WIDTH, OUT_HEIGHT);
}

void setup() {
  Serial.begin(115200);
  delay(1200);
  Serial.println();
  Serial.println("=== Pet Guardian OV7670 Node ===");
  Serial.println("[INFO] OV7670 sem FIFO em ESP32 comum");
  Serial.println("[INFO] SIOD->21, SIOC->22, HREF->35, RESET->3.3V, PWDN->GND");

  connectWifi();
  setupCameraNode();

  server.begin();
  Serial.println("[HTTP] Rotas prontas:");
  Serial.println("[HTTP] /");
  Serial.println("[HTTP] /capture.bmp?reason=manual");
  Serial.println("[HTTP] /status");
}

void loop() {
  serve();
  maintainWifi();
  unsigned long now = millis();
  if (now - lastCommandPollMs >= COMMAND_POLL_INTERVAL_MS) {
    lastCommandPollMs = now;
    pollCameraCommand();
  }
}
