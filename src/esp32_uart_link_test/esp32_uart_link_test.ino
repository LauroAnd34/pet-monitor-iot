#include <HardwareSerial.h>

constexpr uint8_t BITDOGLAB_RX_PIN = 16;  // ESP32 RX2 <- Pico TX
constexpr uint8_t BITDOGLAB_TX_PIN = 13;  // ESP32 TX2 -> Pico RX
constexpr uint32_t UART_BAUD = 115200;
constexpr unsigned long PING_INTERVAL_MS = 2000;

HardwareSerial bitdoglabSerial(2);

unsigned long lastPingMs = 0;
String incomingLine = "";
unsigned long pingCount = 0;

void sendPing() {
  pingCount++;
  String message = "PING " + String(pingCount);
  bitdoglabSerial.println(message);

  Serial.print("[ESP32] Enviado: ");
  Serial.println(message);
}

void readFromBitDogLab() {
  while (bitdoglabSerial.available() > 0) {
    char c = static_cast<char>(bitdoglabSerial.read());

    if (c == '\r') {
      continue;
    }

    if (c == '\n') {
      if (incomingLine.length() > 0) {
        Serial.print("[ESP32] Recebido: ");
        Serial.println(incomingLine);
        incomingLine = "";
      }
      continue;
    }

    incomingLine += c;
  }
}

void readFromUsbSerial() {
  while (Serial.available() > 0) {
    char c = static_cast<char>(Serial.read());

    if (c == 'p' || c == 'P') {
      sendPing();
    } else if (c == 'h' || c == 'H') {
      bitdoglabSerial.println("HELLO_FROM_ESP32");
      Serial.println("[ESP32] Enviado: HELLO_FROM_ESP32");
    }
  }
}

void setup() {
  Serial.begin(115200);
  delay(500);

  bitdoglabSerial.begin(UART_BAUD, SERIAL_8N1, BITDOGLAB_RX_PIN, BITDOGLAB_TX_PIN);

  Serial.println("=== TESTE UART ESP32 <-> BITDOGLAB ===");
  Serial.println("Ligacao esperada:");
  Serial.println("ESP32 GPIO13 (TX2) -> Pico GP9 (RX)");
  Serial.println("ESP32 GPIO16 (RX2) <- Pico GP8 (TX)");
  Serial.println("GND comum entre as placas");
  Serial.println("Comandos no monitor serial:");
  Serial.println("P = envia PING manual");
  Serial.println("H = envia HELLO_FROM_ESP32");
}

void loop() {
  readFromBitDogLab();
  readFromUsbSerial();

  if (millis() - lastPingMs >= PING_INTERVAL_MS) {
    lastPingMs = millis();
    sendPing();
  }
}
