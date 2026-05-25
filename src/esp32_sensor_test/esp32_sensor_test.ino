#include <DHT.h>
#include <ESP32Servo.h>

// ===== Ajuste os pinos conforme sua montagem =====
constexpr uint8_t DHT_PIN = 4;
constexpr uint8_t DHT_TYPE = DHT11;

constexpr uint8_t TRIG_FOOD_PIN = 18;
constexpr uint8_t ECHO_FOOD_PIN = 19;
constexpr uint8_t TRIG_WATER_PIN = 5;
constexpr uint8_t ECHO_WATER_PIN = 17;

constexpr uint8_t SERVO_PIN = 14;

constexpr uint8_t RGB_RED_PIN = 25;
constexpr uint8_t RGB_GREEN_PIN = 26;
constexpr uint8_t RGB_BLUE_PIN = 27;

// Se seu LED RGB for anodo comum, troque para true.
constexpr bool RGB_COMMON_ANODE = false;

constexpr int SERVO_CLOSED_ANGLE = 15;
constexpr int SERVO_OPEN_ANGLE = 100;
constexpr unsigned long SERVO_OPEN_TIME_MS = 1500;
constexpr unsigned long SENSOR_PRINT_INTERVAL_MS = 2000;

DHT dht(DHT_PIN, DHT_TYPE);
Servo feederServo;

unsigned long lastPrintMs = 0;
bool feederOpen = false;
unsigned long feederOpenedAtMs = 0;

void writeRgb(bool redOn, bool greenOn, bool blueOn) {
  int redValue = redOn ? HIGH : LOW;
  int greenValue = greenOn ? HIGH : LOW;
  int blueValue = blueOn ? HIGH : LOW;

  if (RGB_COMMON_ANODE) {
    redValue = redOn ? LOW : HIGH;
    greenValue = greenOn ? LOW : HIGH;
    blueValue = blueOn ? LOW : HIGH;
  }

  digitalWrite(RGB_RED_PIN, redValue);
  digitalWrite(RGB_GREEN_PIN, greenValue);
  digitalWrite(RGB_BLUE_PIN, blueValue);
}

void setWhiteColor() {
  writeRgb(true, true, true);
}

float readDistanceCm(uint8_t trigPin, uint8_t echoPin) {
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);

  long duration = pulseIn(echoPin, HIGH, 30000);
  if (duration <= 0) {
    return -1.0f;
  }

  return duration * 0.0343f / 2.0f;
}

void openFeeder() {
  feederServo.write(SERVO_OPEN_ANGLE);
  feederOpen = true;
  feederOpenedAtMs = millis();
  Serial.println("Comedouro aberto.");
}

void closeFeeder() {
  feederServo.write(SERVO_CLOSED_ANGLE);
  feederOpen = false;
  Serial.println("Comedouro fechado.");
}

void printSensorReadings() {
  float temperature = dht.readTemperature();
  float humidity = dht.readHumidity();
  float foodDistance = readDistanceCm(TRIG_FOOD_PIN, ECHO_FOOD_PIN);
  float waterDistance = readDistanceCm(TRIG_WATER_PIN, ECHO_WATER_PIN);

  Serial.println("------ Leitura dos sensores ------");

  if (isnan(temperature) || isnan(humidity)) {
    Serial.println("DHT11: falha na leitura.");
  } else {
    Serial.print("Temperatura: ");
    Serial.print(temperature, 1);
    Serial.println(" C");

    Serial.print("Umidade: ");
    Serial.print(humidity, 1);
    Serial.println(" %");
  }

  Serial.print("Ultrassonico racao: ");
  if (foodDistance < 0) {
    Serial.println("sem resposta");
  } else {
    Serial.print(foodDistance, 1);
    Serial.println(" cm");
  }

  Serial.print("Ultrassonico agua: ");
  if (waterDistance < 0) {
    Serial.println("sem resposta");
  } else {
    Serial.print(waterDistance, 1);
    Serial.println(" cm");
  }

  Serial.print("Servo: ");
  Serial.println(feederOpen ? "aberto" : "fechado");
  Serial.println("----------------------------------");
}

void handleSerialCommands() {
  while (Serial.available() > 0) {
    char command = static_cast<char>(Serial.read());

    if (command == '\n' || command == '\r') {
      continue;
    }

    if (command == '1') {
      openFeeder();
    } else if (command == '0') {
      closeFeeder();
    } else if (command == 'r' || command == 'R') {
      printSensorReadings();
    } else {
      Serial.println("Comando invalido. Use 1 para abrir, 0 para fechar, R para ler.");
    }
  }
}

void setupPins() {
  pinMode(TRIG_FOOD_PIN, OUTPUT);
  pinMode(ECHO_FOOD_PIN, INPUT);
  pinMode(TRIG_WATER_PIN, OUTPUT);
  pinMode(ECHO_WATER_PIN, INPUT);

  pinMode(RGB_RED_PIN, OUTPUT);
  pinMode(RGB_GREEN_PIN, OUTPUT);
  pinMode(RGB_BLUE_PIN, OUTPUT);
  setWhiteColor();

  feederServo.setPeriodHertz(50);
  feederServo.attach(SERVO_PIN, 500, 2400);
  closeFeeder();
}

void setup() {
  Serial.begin(115200);
  delay(500);

  dht.begin();
  setupPins();

  Serial.println("Teste do ESP32 iniciado.");
  Serial.println("Comandos:");
  Serial.println("1 = abrir comedouro");
  Serial.println("0 = fechar comedouro");
  Serial.println("R = imprimir leitura imediata");
}

void loop() {
  handleSerialCommands();

  if (feederOpen && millis() - feederOpenedAtMs >= SERVO_OPEN_TIME_MS) {
    closeFeeder();
  }

  if (millis() - lastPrintMs >= SENSOR_PRINT_INTERVAL_MS) {
    lastPrintMs = millis();
    printSensorReadings();
  }
}
