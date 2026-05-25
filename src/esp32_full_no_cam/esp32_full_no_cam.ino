#include <WiFi.h>
#include <WebServer.h>
#include <HTTPClient.h>
#include <DHT.h>
#include <ESP32Servo.h>
#include <Preferences.h>

const bool USE_SOFT_AP = true;
const char* WIFI_SSID = "Lauroo";
const char* WIFI_PASSWORD = "Lalalauro23";
const char* AP_SSID = "PetMonitorESP32";
const char* AP_PASSWORD = "pet12345";
IPAddress apIp(192, 168, 4, 1);
IPAddress apGateway(192, 168, 4, 1);
IPAddress apSubnet(255, 255, 255, 0);

const bool WEBHOOK_ENABLED = false;
const char* WEBHOOK_URL = "http://seu-servidor.local/pet-alert";

constexpr uint8_t DHT_PIN = 4;
constexpr uint8_t DHT_TYPE = DHT11; 
constexpr uint8_t TRIG_FOOD_PIN = 18;
constexpr uint8_t ECHO_FOOD_PIN = 19;
constexpr uint8_t TRIG_WATER_PIN = 5;
constexpr uint8_t ECHO_WATER_PIN = 17;
constexpr uint8_t PIR_PIN = 27;
constexpr uint8_t GAS_PIN = 34;
constexpr uint8_t LIGHT_SENSOR_PIN = 35;
constexpr uint8_t SERVO_PIN = 14;
constexpr uint8_t LED_PIN = 2;

constexpr float FOOD_CONTAINER_HEIGHT_CM = 18.0f;
constexpr float WATER_CONTAINER_HEIGHT_CM = 14.0f;
constexpr int GAS_ALERT_THRESHOLD = 1800;
constexpr float HIGH_TEMP_THRESHOLD = 31.0f;
constexpr int LOW_LEVEL_THRESHOLD = 20;
constexpr unsigned long SENSOR_INTERVAL_MS = 3000;
constexpr unsigned long AUTO_FEED_INTERVAL_MS = 8UL * 60UL * 60UL * 1000UL;
constexpr unsigned long ALERT_COOLDOWN_MS = 5UL * 60UL * 1000UL;
constexpr int SERVO_OPEN_ANGLE = 110;
constexpr int SERVO_CLOSED_ANGLE = 20;
constexpr unsigned long SERVO_OPEN_MS = 1500;
constexpr int DEFAULT_DARK_THRESHOLD = 1800;
constexpr unsigned long DEFAULT_LED_DURATION_MS = 120000;

DHT dht(DHT_PIN, DHT_TYPE);
Servo feederServo;
WebServer server(80);
Preferences preferences;

struct LedAutomationConfig {
  bool autoEnabled = true;
  int darkThreshold = DEFAULT_DARK_THRESHOLD;
  unsigned long autoOnDurationMs = DEFAULT_LED_DURATION_MS;
};

struct SensorData {
  float temperatureC = NAN;
  float humidity = NAN;
  int foodLevelPercent = 0;
  int waterLevelPercent = 0;
  int gasRaw = 0;
  int lightRaw = 0;
  bool isDark = false;
  bool motionDetected = false;
  bool ledOn = false;
  bool automaticMode = true;
  bool manualLedMode = false;
  unsigned long lastFeedMs = 0;
  unsigned long bootMs = 0;
  unsigned long ledAutoUntilMs = 0;
  String lastAlert = "Nenhum";
  String appUrl = "";
  String networkMode = "";
};

SensorData state;
LedAutomationConfig ledConfig;

unsigned long lastSensorReadMs = 0;
unsigned long lastFeedScheduleMs = 0;
unsigned long lastAlertSentMs = 0;

String jsonFloatOrNull(float value, int decimals) {
  if (isnan(value)) {
    return "null";
  }

  return String(value, decimals);
}

String escapeJson(const String& value) {
  String escaped = value;
  escaped.replace("\\", "\\\\");
  escaped.replace("\"", "\\\"");
  return escaped;
}

String formatUptime(unsigned long ms) {
  unsigned long totalSeconds = ms / 1000;
  unsigned long hours = totalSeconds / 3600;
  unsigned long minutes = (totalSeconds % 3600) / 60;
  unsigned long seconds = totalSeconds % 60;

  char buffer[32];
  snprintf(buffer, sizeof(buffer), "%02lu:%02lu:%02lu", hours, minutes, seconds);
  return String(buffer);
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

int distanceToPercent(float distanceCm, float containerHeightCm) {
  if (distanceCm < 0) {
    return 0;
  }

  float filledHeight = containerHeightCm - distanceCm;
  float percent = (filledHeight / containerHeightCm) * 100.0f;

  if (percent < 0.0f) {
    percent = 0.0f;
  }
  if (percent > 100.0f) {
    percent = 100.0f;
  }

  return static_cast<int>(percent);
}

void setLed(bool on) {
  state.ledOn = on;
  digitalWrite(LED_PIN, on ? HIGH : LOW);
}

void saveLedConfig() {
  preferences.putBool("led_auto", ledConfig.autoEnabled);
  preferences.putInt("dark_thr", ledConfig.darkThreshold);
  preferences.putULong("led_dur", ledConfig.autoOnDurationMs);
}

void loadLedConfig() {
  preferences.begin("pet-monitor", false);
  ledConfig.autoEnabled = preferences.getBool("led_auto", true);
  ledConfig.darkThreshold = preferences.getInt("dark_thr", DEFAULT_DARK_THRESHOLD);
  ledConfig.autoOnDurationMs = preferences.getULong("led_dur", DEFAULT_LED_DURATION_MS);
}

void sendWebhookAlert(const String& message) {
  state.lastAlert = message;

  if (!WEBHOOK_ENABLED || WiFi.status() != WL_CONNECTED) {
    Serial.println("[ALERTA] " + message);
    return;
  }

  HTTPClient http;
  http.begin(WEBHOOK_URL);
  http.addHeader("Content-Type", "application/json");

  String payload = "{\"message\":\"" + message + "\"}";
  int responseCode = http.POST(payload);

  Serial.print("[WEBHOOK] codigo=");
  Serial.println(responseCode);
  http.end();
}

void maybeSendAlert(const String& message) {
  unsigned long now = millis();
  if (now - lastAlertSentMs < ALERT_COOLDOWN_MS) {
    return;
  }

  lastAlertSentMs = now;
  sendWebhookAlert(message);
}

void dispenseFood() {
  feederServo.write(SERVO_OPEN_ANGLE);
  delay(SERVO_OPEN_MS);
  feederServo.write(SERVO_CLOSED_ANGLE);
  state.lastFeedMs = millis();
  Serial.println("[ACAO] Racao liberada");
}

void evaluateAutoLed() {
  unsigned long now = millis();

  if (state.manualLedMode) {
    return;
  }

  if (state.ledOn && state.ledAutoUntilMs > 0 && now >= state.ledAutoUntilMs) {
    setLed(false);
    state.ledAutoUntilMs = 0;
  }

  if (!ledConfig.autoEnabled) {
    return;
  }

  if (state.isDark && state.motionDetected && !state.ledOn) {
    setLed(true);
    state.ledAutoUntilMs = now + ledConfig.autoOnDurationMs;
    Serial.println("[AUTO] LED ligado por presenca em ambiente escuro");
  }
}

void readSensors() {
  state.temperatureC = dht.readTemperature();
  state.humidity = dht.readHumidity();

  float foodDistance = readDistanceCm(TRIG_FOOD_PIN, ECHO_FOOD_PIN);
  float waterDistance = readDistanceCm(TRIG_WATER_PIN, ECHO_WATER_PIN);

  state.foodLevelPercent = distanceToPercent(foodDistance, FOOD_CONTAINER_HEIGHT_CM);
  state.waterLevelPercent = distanceToPercent(waterDistance, WATER_CONTAINER_HEIGHT_CM);
  state.gasRaw = analogRead(GAS_PIN);
  state.lightRaw = analogRead(LIGHT_SENSOR_PIN);
  state.isDark = state.lightRaw <= ledConfig.darkThreshold;
  state.motionDetected = digitalRead(PIR_PIN) == HIGH;

  if (!isnan(state.temperatureC) && state.temperatureC >= HIGH_TEMP_THRESHOLD) {
    maybeSendAlert("Temperatura alta detectada no ambiente do pet.");
  }
  if (state.foodLevelPercent <= LOW_LEVEL_THRESHOLD) {
    maybeSendAlert("Nivel de racao baixo.");
  }
  if (state.waterLevelPercent <= LOW_LEVEL_THRESHOLD) {
    maybeSendAlert("Nivel de agua baixo.");
  }
  if (state.gasRaw >= GAS_ALERT_THRESHOLD) {
    maybeSendAlert("Possivel gas detectado no ambiente.");
  }

  evaluateAutoLed();
}

String buildJsonStatus() {
  String json = "{";
  json += "\"temperatureC\":" + jsonFloatOrNull(state.temperatureC, 1) + ",";
  json += "\"humidity\":" + jsonFloatOrNull(state.humidity, 1) + ",";
  json += "\"foodLevelPercent\":" + String(state.foodLevelPercent) + ",";
  json += "\"waterLevelPercent\":" + String(state.waterLevelPercent) + ",";
  json += "\"gasRaw\":" + String(state.gasRaw) + ",";
  json += "\"lightRaw\":" + String(state.lightRaw) + ",";
  json += "\"isDark\":" + String(state.isDark ? "true" : "false") + ",";
  json += "\"motionDetected\":" + String(state.motionDetected ? "true" : "false") + ",";
  json += "\"ledOn\":" + String(state.ledOn ? "true" : "false") + ",";
  json += "\"manualLedMode\":" + String(state.manualLedMode ? "true" : "false") + ",";
  json += "\"automaticMode\":" + String(state.automaticMode ? "true" : "false") + ",";
  json += "\"autoLedEnabled\":" + String(ledConfig.autoEnabled ? "true" : "false") + ",";
  json += "\"darkThreshold\":" + String(ledConfig.darkThreshold) + ",";
  json += "\"ledDurationSeconds\":" + String(ledConfig.autoOnDurationMs / 1000UL) + ",";
  json += "\"uptime\":\"" + formatUptime(millis() - state.bootMs) + "\",";
  json += "\"networkMode\":\"" + escapeJson(state.networkMode) + "\",";
  json += "\"appUrl\":\"" + escapeJson(state.appUrl) + "\",";
  json += "\"lastAlert\":\"" + escapeJson(state.lastAlert) + "\"";
  json += "}";
  return json;
}

String htmlPage() {
  return R"rawliteral(
<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Pet Monitor IoT</title>
  <style>
    :root {
      --bg: #f6f1e8;
      --panel: #fffaf2;
      --ink: #243127;
      --accent: #d97706;
      --accent-2: #0f766e;
      --danger: #b91c1c;
      --line: #e7d7be;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      font-family: "Trebuchet MS", Verdana, sans-serif;
      background: radial-gradient(circle at top, #fff8ef 0%, var(--bg) 55%, #eadfcf 100%);
      color: var(--ink);
    }
    .wrap {
      max-width: 1100px;
      margin: 0 auto;
      padding: 24px;
    }
    h1 {
      margin: 0 0 8px;
      font-size: clamp(2rem, 4vw, 3.2rem);
    }
    .subtitle {
      margin: 0 0 24px;
      color: #5a695e;
    }
    .grid {
      display: grid;
      gap: 16px;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    }
    .card {
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 18px;
      padding: 18px;
      box-shadow: 0 12px 30px rgba(80, 55, 20, 0.08);
    }
    .label {
      font-size: 0.85rem;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: #7c6a53;
      margin-bottom: 6px;
    }
    .value {
      font-size: 2rem;
      font-weight: 700;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin: 20px 0;
    }
    button {
      border: 0;
      border-radius: 999px;
      padding: 12px 18px;
      font-size: 1rem;
      cursor: pointer;
      color: white;
      background: var(--accent);
    }
    button.alt { background: var(--accent-2); }
    button.danger { background: var(--danger); }
    .status {
      margin-top: 18px;
      color: #5a695e;
    }
    .network {
      margin-top: 20px;
      padding: 14px 16px;
      border-radius: 14px;
      background: #fff2dd;
      border: 1px solid #efd2a2;
      color: #6f4b12;
    }
    input[type="number"] {
      width: 100%;
      padding: 10px;
      border-radius: 10px;
      border: 1px solid var(--line);
      margin-top: 6px;
    }
  </style>
</head>
<body>
  <div class="wrap">
    <h1>Painel do Pet</h1>
    <p class="subtitle">Monitoramento ambiental e automacao local no ESP32.</p>

    <div class="grid">
      <div class="card"><div class="label">Temperatura</div><div class="value" id="temperature">--</div></div>
      <div class="card"><div class="label">Umidade</div><div class="value" id="humidity">--</div></div>
      <div class="card"><div class="label">Racao</div><div class="value" id="food">--</div></div>
      <div class="card"><div class="label">Agua</div><div class="value" id="water">--</div></div>
      <div class="card"><div class="label">Gas</div><div class="value" id="gas">--</div></div>
      <div class="card"><div class="label">Luminosidade</div><div class="value" id="light">--</div></div>
      <div class="card"><div class="label">Presenca</div><div class="value" id="motion">--</div></div>
      <div class="card"><div class="label">LED</div><div class="value" id="ledState">--</div></div>
    </div>

    <div class="actions">
      <button onclick="postAction('/api/feed')">Liberar racao</button>
      <button class="alt" onclick="postAction('/api/led?state=on')">Ligar LED</button>
      <button class="danger" onclick="postAction('/api/led?state=off')">Desligar LED</button>
      <button class="alt" onclick="postAction('/api/led?state=auto')">Voltar ao automatico</button>
    </div>

    <div class="network">
      <div id="networkMode">Modo de rede: --</div>
      <div id="appUrl">Acesso do app: --</div>
    </div>

    <div class="card" style="margin-top: 20px;">
      <div class="label">Automacao do LED</div>
      <div style="display:grid; gap:12px; margin-top:12px;">
        <label>
          <input type="checkbox" id="autoLedEnabled">
          Ativar automacao por presenca + ambiente escuro
        </label>
        <label>
          Limite de escuridao (valor analogico)
          <input id="darkThreshold" type="number" min="0" max="4095" value="1800">
        </label>
        <label>
          Tempo ligado automaticamente (segundos)
          <input id="ledDurationSeconds" type="number" min="5" max="3600" value="120">
        </label>
        <button class="alt" onclick="saveLedConfig()">Salvar configuracao do LED</button>
      </div>
    </div>

    <div class="status">
      <div id="alert">Ultimo alerta: --</div>
      <div id="uptime">Tempo ligado: --</div>
    </div>
  </div>

  <script>
    async function refreshStatus() {
      const response = await fetch('/api/status');
      const data = await response.json();

      document.getElementById('temperature').textContent = `${data.temperatureC} C`;
      document.getElementById('humidity').textContent = `${data.humidity} %`;
      document.getElementById('food').textContent = `${data.foodLevelPercent} %`;
      document.getElementById('water').textContent = `${data.waterLevelPercent} %`;
      document.getElementById('gas').textContent = data.gasRaw;
      document.getElementById('light').textContent = `${data.lightRaw} (${data.isDark ? 'Escuro' : 'Claro'})`;
      document.getElementById('motion').textContent = data.motionDetected ? 'Ativo' : 'Parado';
      document.getElementById('ledState').textContent = data.ledOn ? (data.manualLedMode ? 'Manual' : 'Ligado') : 'Desligado';
      document.getElementById('alert').textContent = `Ultimo alerta: ${data.lastAlert}`;
      document.getElementById('uptime').textContent = `Tempo ligado: ${data.uptime}`;
      document.getElementById('networkMode').textContent = `Modo de rede: ${data.networkMode}`;
      document.getElementById('appUrl').textContent = `Acesso do app: ${data.appUrl}`;
      document.getElementById('autoLedEnabled').checked = data.autoLedEnabled;
      document.getElementById('darkThreshold').value = data.darkThreshold;
      document.getElementById('ledDurationSeconds').value = data.ledDurationSeconds;
    }

    async function postAction(url) {
      await fetch(url, { method: 'POST' });
      refreshStatus();
    }

    async function saveLedConfig() {
      const enabled = document.getElementById('autoLedEnabled').checked ? 'true' : 'false';
      const darkThreshold = document.getElementById('darkThreshold').value;
      const ledDurationSeconds = document.getElementById('ledDurationSeconds').value;
      const body = new URLSearchParams({ enabled, darkThreshold, ledDurationSeconds });

      await fetch('/api/config/led', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body
      });

      refreshStatus();
    }

    refreshStatus();
    setInterval(refreshStatus, 3000);
  </script>
</body>
</html>
)rawliteral";
}

void handleRoot() {
  server.send(200, "text/html; charset=utf-8", htmlPage());
}

void handleStatus() {
  server.send(200, "application/json", buildJsonStatus());
}

void handleFeed() {
  dispenseFood();
  server.send(200, "application/json", "{\"ok\":true,\"message\":\"Racao liberada\"}");
}

void handleLed() {
  String stateParam = server.arg("state");
  if (stateParam == "on") {
    state.manualLedMode = true;
    state.ledAutoUntilMs = 0;
    setLed(true);
  } else if (stateParam == "off") {
    state.manualLedMode = false;
    state.ledAutoUntilMs = 0;
    setLed(false);
  } else if (stateParam == "auto") {
    state.manualLedMode = false;
    state.ledAutoUntilMs = 0;
    evaluateAutoLed();
  } else {
    server.send(400, "application/json", "{\"ok\":false,\"message\":\"Parametro state invalido\"}");
    return;
  }

  server.send(200, "application/json", "{\"ok\":true}");
}

void handleLedConfig() {
  if (server.hasArg("enabled")) {
    ledConfig.autoEnabled = server.arg("enabled") == "true";
  }

  if (server.hasArg("darkThreshold")) {
    int threshold = server.arg("darkThreshold").toInt();
    ledConfig.darkThreshold = constrain(threshold, 0, 4095);
  }

  if (server.hasArg("ledDurationSeconds")) {
    unsigned long durationSeconds = static_cast<unsigned long>(server.arg("ledDurationSeconds").toInt());
    if (durationSeconds < 5UL) {
      durationSeconds = 5UL;
    }
    if (durationSeconds > 3600UL) {
      durationSeconds = 3600UL;
    }
    ledConfig.autoOnDurationMs = durationSeconds * 1000UL;
  }

  saveLedConfig();
  evaluateAutoLed();
  server.send(200, "application/json", "{\"ok\":true,\"message\":\"Configuracao do LED salva\"}");
}

void startSoftAp() {
  WiFi.mode(WIFI_AP);
  WiFi.softAPConfig(apIp, apGateway, apSubnet);
  WiFi.softAP(AP_SSID, AP_PASSWORD);

  IPAddress ip = WiFi.softAPIP();
  state.networkMode = "Access Point";
  state.appUrl = "http://" + ip.toString();

  Serial.println();
  Serial.println("Modo Access Point ativo");
  Serial.print("Rede Wi-Fi: ");
  Serial.println(AP_SSID);
  Serial.print("Senha Wi-Fi: ");
  Serial.println(AP_PASSWORD);
  Serial.print("App local: ");
  Serial.println(state.appUrl);
}

void connectToWiFi() {
  if (USE_SOFT_AP) {
    startSoftAp();
    return;
  }

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  Serial.print("Conectando ao Wi-Fi");
  unsigned long startAttemptMs = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - startAttemptMs < 15000) {
    delay(500);
    Serial.print(".");
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println();
    Serial.print("IP ESP32 principal: ");
    Serial.println(WiFi.localIP());
    state.networkMode = "Cliente Wi-Fi";
    state.appUrl = "http://" + WiFi.localIP().toString();
    return;
  }

  startSoftAp();
}

void setupPins() {
  pinMode(TRIG_FOOD_PIN, OUTPUT);
  pinMode(ECHO_FOOD_PIN, INPUT);
  pinMode(TRIG_WATER_PIN, OUTPUT);
  pinMode(ECHO_WATER_PIN, INPUT);
  pinMode(PIR_PIN, INPUT);
  pinMode(GAS_PIN, INPUT);
  pinMode(LIGHT_SENSOR_PIN, INPUT);
  pinMode(LED_PIN, OUTPUT);
  setLed(false);

  feederServo.setPeriodHertz(50);
  feederServo.attach(SERVO_PIN, 500, 2400);
  feederServo.write(SERVO_CLOSED_ANGLE);
}

void setupServer() {
  server.on("/", HTTP_GET, handleRoot);
  server.on("/api/status", HTTP_GET, handleStatus);
  server.on("/api/feed", HTTP_POST, handleFeed);
  server.on("/api/led", HTTP_POST, handleLed);
  server.on("/api/config/led", HTTP_POST, handleLedConfig);
  server.begin();
}

void setup() {
  Serial.begin(115200);
  delay(500);

  state.bootMs = millis();
  loadLedConfig();
  dht.begin();
  setupPins();
  connectToWiFi();
  readSensors();
  setupServer();
}

void loop() {
  server.handleClient();

  unsigned long now = millis();
  if (now - lastSensorReadMs >= SENSOR_INTERVAL_MS) {
    lastSensorReadMs = now;
    readSensors();
  }

  if (state.automaticMode && now - lastFeedScheduleMs >= AUTO_FEED_INTERVAL_MS) {
    lastFeedScheduleMs = now;
    dispenseFood();
  }
}
