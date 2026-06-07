#include <Wire.h>
#include <WiFi.h>
#include <WiFiClient.h>
#include <OV7670.h>

const char* WIFI_SSID = "Lauroo";
const char* WIFI_PASSWORD = "Lalalauro23";

#define CAM_RES QQVGA
#define CAM_WIDTH 160
#define CAM_HEIGHT 120
#define CAM_COLOR_MODE RGB565

constexpr int CROP_LEFT = 8;
constexpr int CROP_RIGHT = 8;
constexpr int CROP_TOP = 10;
constexpr int CROP_BOTTOM = 12;
constexpr int OUT_WIDTH = CAM_WIDTH - CROP_LEFT - CROP_RIGHT;
constexpr int OUT_HEIGHT = CAM_HEIGHT - CROP_TOP - CROP_BOTTOM;

const camera_config_t cam_conf = {
  .D0 = 27,
  .D1 = 5,
  .D2 = 2,
  .D3 = 15,
  .D4 = 14,
  .D5 = 13,
  .D6 = 12,
  .D7 = 4,
  .XCLK = 32,
  .PCLK = 33,
  .VSYNC = 34,
  .xclk_freq_hz = 10000000,
  .ledc_timer = LEDC_TIMER_0,
  .ledc_channel = LEDC_CHANNEL_0
};

OV7670 cam;
WiFiServer server(80);
constexpr size_t BMP_HEADER_SIZE = 54;
constexpr size_t BMP_ROW_SIZE = ((OUT_WIDTH * 3) + 3) & ~3;
unsigned char bmpHeader[BMP_HEADER_SIZE];
uint8_t frameBuffer[CAM_WIDTH * CAM_HEIGHT * 2];
uint8_t bmpRowBuffer[BMP_ROW_SIZE];

unsigned long frameCount = 0;
unsigned long lastFrameMs = 0;
String lastReason = "idle";

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
  const uint16_t* srcRow = reinterpret_cast<const uint16_t*>(frameBuffer + (sourceRowIndex * CAM_WIDTH * 2));

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
  client.println("}");
}

void sendCapture(WiFiClient& client, const String& reason) {
  lastReason = reason;
  cam.getFrame(frameBuffer);
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
  esp_err_t err = cam.init(&cam_conf, CAM_RES, CAM_COLOR_MODE);
  if (err != ESP_OK) {
    Serial.print("[CAM] Falha no init: ");
    Serial.println((int)err);
    while (true) {
      delay(1000);
    }
  }

  cam.vflip(false);
  cam.setAGC(1);
  cam.setAEC(1);
  cam.setAWB(0);
  cam.setAWBR(72);
  cam.setAWBG(64);
  cam.setAWBB(88);
  cam.setContrast(80);
  cam.setBright(12);
  cam.setPCLK(3, DBLV_CLK_x4);
  constructBmpHeader(bmpHeader, OUT_WIDTH, OUT_HEIGHT);
  Serial.printf("[CAM] MID = %X\n", cam.getMID());
  Serial.printf("[CAM] PID = %X\n", cam.getPID());
  Serial.printf("[CAM] Resolucao bruta = %dx%d\n", CAM_WIDTH, CAM_HEIGHT);
  Serial.printf("[CAM] Resolucao exibida = %dx%d\n", OUT_WIDTH, OUT_HEIGHT);
}

void setup() {
  Serial.begin(115200);
  delay(1200);
  Serial.println();
  Serial.println("=== Pet Guardian OV7670 Node ===");
  Serial.println("[INFO] OV7670 sem FIFO em ESP32 comum");
  Serial.println("[INFO] SIOD->21, SIOC->22, HREF sem uso direto nesta biblioteca, RESET->3.3V, PWDN->GND");

  Wire.begin();
  Wire.setClock(400000);

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
}
