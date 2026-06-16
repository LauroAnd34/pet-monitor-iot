#include <WiFi.h>
#include <WebServer.h>
#include <HTTPClient.h>
#include <DHT.h>
#include <Preferences.h>

const bool USE_SOFT_AP = false;
// Credenciais de exemplo. Substitua localmente antes de gravar a placa.
const char* WIFI_SSID = "SEU_WIFI";
const char* WIFI_PASSWORD = "SUA_SENHA";
const char* AP_SSID = "Pet-Sistema";
const char* AP_PASSWORD = "pet12345";
IPAddress apIp(192, 168, 4, 1);
IPAddress apGateway(192, 168, 4, 1);
IPAddress apSubnet(255, 255, 255, 0);

const bool WEBHOOK_ENABLED = false;
const char* WEBHOOK_URL = "";
const bool CAMERA_INTEGRATION_READY = true;
const bool CLOUD_SYNC_ENABLED = false;
const char* CLOUD_INGEST_URL = "https://SEU_PROJETO.supabase.co/functions/v1/ingest-telemetry";
const char* CLOUD_DEVICE_TOKEN = "SEU_DEVICE_TOKEN";
constexpr unsigned long CLOUD_SYNC_INTERVAL_MS = 15000;
constexpr unsigned long WIFI_RETRY_INTERVAL_MS = 10000;
constexpr unsigned long WIFI_FULL_RESTART_INTERVAL_MS = 60000;

constexpr uint8_t DHT_PIN = 4;
constexpr uint8_t DHT_TYPE = DHT11;
constexpr uint8_t TRIG_FOOD_PIN = 18;
constexpr uint8_t ECHO_FOOD_PIN = 19;
constexpr uint8_t TRIG_WATER_PIN = 5;
constexpr uint8_t ECHO_WATER_PIN = 17;
constexpr uint8_t PIR_PIN = 27;
constexpr uint8_t GAS_PIN = 34;
constexpr uint8_t LDR_PIN = 35;
constexpr uint8_t BITDOGLAB_LAMP_SIGNAL_PIN = 13;
constexpr uint8_t BUZZER_PIN = 32;

// Ponte H - Canal A: motor do comedouro
constexpr uint8_t FEED_IN1_PIN = 14;
constexpr uint8_t FEED_IN2_PIN = 12;
constexpr uint8_t FEED_EN_PIN  = 25;

// Ponte H - Canal B: bomba d'agua
constexpr uint8_t PUMP_IN3_PIN = 26;
constexpr uint8_t PUMP_IN4_PIN = 23;
constexpr uint8_t PUMP_EN_PIN  = 33;

constexpr float FOOD_CONTAINER_HEIGHT_CM = 10.0f;
constexpr float WATER_CONTAINER_HEIGHT_CM = 14.0f;
constexpr int GAS_ALERT_THRESHOLD = 1800;
constexpr float HIGH_TEMP_THRESHOLD = 31.0f;
constexpr int LOW_LEVEL_THRESHOLD = 20;
constexpr unsigned long SENSOR_INTERVAL_MS = 2500;
constexpr unsigned long ALERT_COOLDOWN_MS = 5UL * 60UL * 1000UL;
constexpr unsigned long FEED_MOTOR_RUN_MS = 2800;
constexpr unsigned long DEFAULT_FEED_INTERVAL_MS = 8UL * 60UL * 60UL * 1000UL;
constexpr unsigned long DEFAULT_PUMP_DURATION_MS = 12000;
constexpr unsigned long DEFAULT_LAMP_DURATION_MS = 120000;
constexpr unsigned long PUMP_COOLDOWN_MS = 10UL * 60UL * 1000UL;
constexpr int DEFAULT_DARK_THRESHOLD = 1800;
constexpr int DEFAULT_LAMP_BRIGHTNESS = 7;
constexpr int INTERNET_CONNECTED_MELODY_TEMPO_MS = 140;

DHT dht(DHT_PIN, DHT_TYPE);
WebServer server(80);
Preferences preferences;

struct LampConfig {
  bool autoEnabled = true;
  int darkThreshold = DEFAULT_DARK_THRESHOLD;
  unsigned long durationMs = DEFAULT_LAMP_DURATION_MS;
  int brightness = DEFAULT_LAMP_BRIGHTNESS;
};

struct FeedConfig {
  bool autoEnabled = true;
  unsigned long intervalMs = DEFAULT_FEED_INTERVAL_MS;
};

struct PumpConfig {
  bool autoEnabled = false;
  unsigned long durationMs = DEFAULT_PUMP_DURATION_MS;
  int lowLevelThreshold = LOW_LEVEL_THRESHOLD;
};

struct PicoLampState {
  bool lampOn = false;
  bool manualMode = false;
  int brightness = DEFAULT_LAMP_BRIGHTNESS;
  String lastMessage = "Controle por pino digital";
};

struct SensorSnapshot {
  float temperatureC = NAN;
  float humidity = NAN;
  int foodLevelPercent = 0;
  int waterLevelPercent = 0;
  int gasRaw = 0;
  int lightRaw = 0;
  bool isDark = false;
  bool motionDetected = false;
  bool pumpOn = false;
  bool manualPumpMode = false;
  bool cameraReady = false;
  unsigned long lastFeedMs = 0;
  unsigned long lastPumpMs = 0;
  unsigned long bootMs = 0;
  String networkMode = "";
  String appUrl = "";
  String lastAlert = "Nenhum";
  bool cloudSyncEnabled = CLOUD_SYNC_ENABLED;
  bool cloudConnected = false;
  unsigned long lastCloudSyncMs = 0;
  String lastCloudStatus = "Nuvem desativada";
};

LampConfig lampConfig;
FeedConfig feedConfig;
PumpConfig pumpConfig;
PicoLampState picoState;
SensorSnapshot state;

unsigned long lastSensorReadMs = 0;
unsigned long lastAlertSentMs = 0;
unsigned long lampAutoUntilMs = 0;
unsigned long lastCloudAttemptMs = 0;
unsigned long lastWiFiRetryMs = 0;
unsigned long lastWiFiFullRestartMs = 0;
bool wifiWasConnected = false;
bool fallbackAccessPointActive = false;

String wifiStatusLabel(wl_status_t status) {
  switch (status) {
    case WL_CONNECTED: return "conectado";
    case WL_NO_SSID_AVAIL: return "rede nao encontrada";
    case WL_CONNECT_FAILED: return "senha recusada ou falha de autenticacao";
    case WL_CONNECTION_LOST: return "conexao perdida";
    case WL_DISCONNECTED: return "desconectado";
    case WL_IDLE_STATUS: return "aguardando conexao";
    default: return "estado " + String(static_cast<int>(status));
  }
}

bool cloudConfigIsValid() {
  String ingestUrl = String(CLOUD_INGEST_URL);
  String deviceToken = String(CLOUD_DEVICE_TOKEN);
  return ingestUrl.startsWith("https://") &&
         ingestUrl.indexOf("SEU_PROJETO") < 0 &&
         deviceToken.length() > 0 &&
         deviceToken.indexOf("SEU_DEVICE_TOKEN") < 0;
}

String jsonFloatOrNull(float value, int decimals) {
  if (isnan(value)) return "null";
  return String(value, decimals);
}

String escapeJson(const String& value) {
  String escaped = value;
  escaped.replace("\\", "\\\\");
  escaped.replace("\"", "\\\"");
  return escaped;
}

String formatDuration(unsigned long ms) {
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
  if (duration <= 0) return -1.0f;

  return duration * 0.0343f / 2.0f;
}

int distanceToPercent(float distanceCm, float containerHeightCm) {
  // O ultrassonico mede o espaco vazio: distancia pequena significa recipiente cheio.
  if (distanceCm < 0) return 0;

  float filledHeight = containerHeightCm - distanceCm;
  float percent = (filledHeight / containerHeightCm) * 100.0f;
  percent = constrain(percent, 0.0f, 100.0f);

  return static_cast<int>(percent);
}

void playInternetConnectedMelody() {
  const int notes[] = { 659, 784, 988, 1175, 988, 1175 };
  const int durations[] = { 1, 1, 1, 2, 1, 3 };
  const size_t noteCount = sizeof(notes) / sizeof(notes[0]);

  for (size_t i = 0; i < noteCount; i++) {
    int noteDurationMs = INTERNET_CONNECTED_MELODY_TEMPO_MS * durations[i];
    tone(BUZZER_PIN, notes[i], noteDurationMs);
    delay(noteDurationMs + 35);
  }

  noTone(BUZZER_PIN);
}

void saveConfig() {
  preferences.putBool("lamp_auto", lampConfig.autoEnabled);
  preferences.putInt("dark_thr", lampConfig.darkThreshold);
  preferences.putULong("lamp_dur", lampConfig.durationMs);
  preferences.putInt("lamp_bri", lampConfig.brightness);

  preferences.putBool("feed_auto", feedConfig.autoEnabled);
  preferences.putULong("feed_int", feedConfig.intervalMs);

  preferences.putBool("pump_auto", pumpConfig.autoEnabled);
  preferences.putULong("pump_dur", pumpConfig.durationMs);
  preferences.putInt("pump_low", pumpConfig.lowLevelThreshold);
}

void loadConfig() {
  preferences.begin("pet-dual", false);

  lampConfig.autoEnabled = preferences.getBool("lamp_auto", true);
  lampConfig.darkThreshold = preferences.getInt("dark_thr", DEFAULT_DARK_THRESHOLD);
  lampConfig.durationMs = preferences.getULong("lamp_dur", DEFAULT_LAMP_DURATION_MS);
  lampConfig.brightness = preferences.getInt("lamp_bri", DEFAULT_LAMP_BRIGHTNESS);

  feedConfig.autoEnabled = preferences.getBool("feed_auto", true);
  feedConfig.intervalMs = preferences.getULong("feed_int", DEFAULT_FEED_INTERVAL_MS);

  pumpConfig.autoEnabled = preferences.getBool("pump_auto", false);
  pumpConfig.durationMs = preferences.getULong("pump_dur", DEFAULT_PUMP_DURATION_MS);
  pumpConfig.lowLevelThreshold = preferences.getInt("pump_low", LOW_LEVEL_THRESHOLD);
}

void setFeedMotor(bool on) {
  if (on) {
    digitalWrite(FEED_IN1_PIN, HIGH);
    digitalWrite(FEED_IN2_PIN, LOW);
    digitalWrite(FEED_EN_PIN, HIGH);
  } else {
    digitalWrite(FEED_IN1_PIN, LOW);
    digitalWrite(FEED_IN2_PIN, LOW);
    digitalWrite(FEED_EN_PIN, LOW);
  }
}

void setPump(bool on) {
  state.pumpOn = on;

  if (on) {
    digitalWrite(PUMP_IN3_PIN, HIGH);
    digitalWrite(PUMP_IN4_PIN, LOW);
    digitalWrite(PUMP_EN_PIN, HIGH);
  } else {
    digitalWrite(PUMP_IN3_PIN, LOW);
    digitalWrite(PUMP_IN4_PIN, LOW);
    digitalWrite(PUMP_EN_PIN, LOW);
  }
}

void setPumpForDuration(unsigned long durationMs) {
  setPump(true);
  delay(durationMs);
  setPump(false);
  state.lastPumpMs = millis();
}

void sendWebhookAlert(const String& message) {
  state.lastAlert = message;

  if (!WEBHOOK_ENABLED || WiFi.status() != WL_CONNECTED || String(WEBHOOK_URL).length() == 0) {
    Serial.println("[ALERTA] " + message);
    return;
  }

  HTTPClient http;
  http.begin(WEBHOOK_URL);
  http.addHeader("Content-Type", "application/json");

  String payload = "{\"message\":\"" + message + "\"}";
  http.POST(payload);
  http.end();
}

void maybeSendAlert(const String& message) {
  unsigned long now = millis();
  // Evita spam de alertas: uma mesma condicao fisica pode durar varios ciclos.
  if (now - lastAlertSentMs < ALERT_COOLDOWN_MS) return;

  lastAlertSentMs = now;
  sendWebhookAlert(message);
}

void runFeedMotor(unsigned long durationMs) {
  setFeedMotor(true);
  delay(durationMs);
  setFeedMotor(false);
}

void dispenseFood() {
  runFeedMotor(FEED_MOTOR_RUN_MS);
  state.lastFeedMs = millis();
  Serial.println("[ACAO] Motor do comedouro acionado");
}

void picoLampOn(bool manualMode) {
  picoState.manualMode = manualMode;
  picoState.lampOn = true;
  picoState.brightness = lampConfig.brightness;
  digitalWrite(BITDOGLAB_LAMP_SIGNAL_PIN, HIGH);
}

void picoLampOff(bool manualMode) {
  picoState.manualMode = manualMode;
  picoState.lampOn = false;
  digitalWrite(BITDOGLAB_LAMP_SIGNAL_PIN, LOW);
}

void evaluateAutomation() {
  unsigned long now = millis();

  // Alimentacao automatica usa intervalo salvo em memoria nao volatil.
  if (feedConfig.autoEnabled && now - state.lastFeedMs >= feedConfig.intervalMs) {
    dispenseFood();
  }

  // A lampada so liga sozinha quando ha escuro e movimento; comando manual tem prioridade.
  if (lampConfig.autoEnabled && state.isDark && state.motionDetected && !picoState.manualMode) {
    if (now >= lampAutoUntilMs) {
      picoLampOn(false);
      lampAutoUntilMs = now + lampConfig.durationMs;
    }
  }

  // Depois da janela configurada, a lampada volta ao modo desligado automaticamente.
  if (!picoState.manualMode && picoState.lampOn && lampAutoUntilMs > 0 && now >= lampAutoUntilMs) {
    picoLampOff(false);
    lampAutoUntilMs = 0;
  }

  // A bomba respeita um cooldown para nao ficar ligando continuamente quando o nivel esta baixo.
  if (pumpConfig.autoEnabled &&
      state.waterLevelPercent <= pumpConfig.lowLevelThreshold &&
      now - state.lastPumpMs >= PUMP_COOLDOWN_MS) {
    setPumpForDuration(pumpConfig.durationMs);
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
  state.lightRaw = analogRead(LDR_PIN);
  state.isDark = state.lightRaw <= lampConfig.darkThreshold;
  state.motionDetected = digitalRead(PIR_PIN) == HIGH;

  // As decisoes abaixo transformam leituras brutas em alertas compreensiveis para o app.
  if (!isnan(state.temperatureC) && state.temperatureC >= HIGH_TEMP_THRESHOLD) {
    maybeSendAlert("Temperatura alta detectada.");
  }
  if (state.foodLevelPercent <= LOW_LEVEL_THRESHOLD) {
    maybeSendAlert("Nivel de racao baixo.");
  }
  if (state.waterLevelPercent <= pumpConfig.lowLevelThreshold) {
    maybeSendAlert("Nivel de agua baixo.");
  }
  if (state.gasRaw >= GAS_ALERT_THRESHOLD) {
    maybeSendAlert("Possivel gas detectado.");
  }

  evaluateAutomation();
}

void syncToCloud() {
  state.cloudSyncEnabled = CLOUD_SYNC_ENABLED;

  // O modo local continua funcionando mesmo quando a sincronizacao em nuvem esta desativada.
  if (!CLOUD_SYNC_ENABLED) {
    state.cloudConnected = false;
    state.lastCloudStatus = "Nuvem desativada";
    return;
  }

  if (WiFi.status() != WL_CONNECTED) {
    state.cloudConnected = false;
    state.lastCloudStatus = "Wi-Fi " + wifiStatusLabel(WiFi.status());
    Serial.println("[CLOUD] Telemetria aguardando Wi-Fi. Estado: " + wifiStatusLabel(WiFi.status()) + ".");
    return;
  }

  if (!cloudConfigIsValid()) {
    state.cloudConnected = false;
    state.lastCloudStatus = "URL ou token da nuvem invalidos";
    Serial.println("[CLOUD] URL ou token da nuvem nao configurados corretamente.");
    return;
  }

  HTTPClient http;
  http.begin(CLOUD_INGEST_URL);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("x-device-token", CLOUD_DEVICE_TOKEN);

  // Payload simples em JSON facilita depuracao e integracao com Supabase e app Android.
  String payload = "{";
  payload += "\"temperatureC\":" + jsonFloatOrNull(state.temperatureC, 1) + ",";
  payload += "\"humidity\":" + jsonFloatOrNull(state.humidity, 1) + ",";
  payload += "\"foodLevelPercent\":" + String(state.foodLevelPercent) + ",";
  payload += "\"waterLevelPercent\":" + String(state.waterLevelPercent) + ",";
  payload += "\"gasRaw\":" + String(state.gasRaw) + ",";
  payload += "\"lightRaw\":" + String(state.lightRaw) + ",";
  payload += "\"isDark\":" + String(state.isDark ? "true" : "false") + ",";
  payload += "\"motionDetected\":" + String(state.motionDetected ? "true" : "false") + ",";
  payload += "\"pumpOn\":" + String(state.pumpOn ? "true" : "false") + ",";
  payload += "\"lampOn\":" + String(picoState.lampOn ? "true" : "false") + ",";
  payload += "\"feedMotorOn\":false,";
  payload += "\"lastAlert\":\"" + escapeJson(state.lastAlert) + "\"";
  payload += "}";

  int httpCode = http.POST(payload);
  if (httpCode >= 200 && httpCode < 300) {
    state.cloudConnected = true;
    state.lastCloudSyncMs = millis();
    state.lastCloudStatus = "Ultimo envio OK";
  } else {
    state.cloudConnected = false;
    state.lastCloudStatus = "Falha HTTP " + String(httpCode);
  }

  http.end();
}

String buildStatusJson() {
  String json = "{";
  json += "\"temperatureC\":" + jsonFloatOrNull(state.temperatureC, 1) + ",";
  json += "\"humidity\":" + jsonFloatOrNull(state.humidity, 1) + ",";
  json += "\"foodLevelPercent\":" + String(state.foodLevelPercent) + ",";
  json += "\"waterLevelPercent\":" + String(state.waterLevelPercent) + ",";
  json += "\"gasRaw\":" + String(state.gasRaw) + ",";
  json += "\"lightRaw\":" + String(state.lightRaw) + ",";
  json += "\"isDark\":" + String(state.isDark ? "true" : "false") + ",";
  json += "\"motionDetected\":" + String(state.motionDetected ? "true" : "false") + ",";
  json += "\"pumpOn\":" + String(state.pumpOn ? "true" : "false") + ",";
  json += "\"manualPumpMode\":" + String(state.manualPumpMode ? "true" : "false") + ",";
  json += "\"picoConnected\":true,";
  json += "\"lampOn\":" + String(picoState.lampOn ? "true" : "false") + ",";
  json += "\"lampManualMode\":" + String(picoState.manualMode ? "true" : "false") + ",";
  json += "\"lampBrightness\":" + String(picoState.brightness) + ",";
  json += "\"autoLampEnabled\":" + String(lampConfig.autoEnabled ? "true" : "false") + ",";
  json += "\"darkThreshold\":" + String(lampConfig.darkThreshold) + ",";
  json += "\"lampDurationSeconds\":" + String(lampConfig.durationMs / 1000UL) + ",";
  json += "\"feedAutoEnabled\":" + String(feedConfig.autoEnabled ? "true" : "false") + ",";
  json += "\"feedIntervalHours\":" + String(feedConfig.intervalMs / 3600000UL) + ",";
  json += "\"pumpAutoEnabled\":" + String(pumpConfig.autoEnabled ? "true" : "false") + ",";
  json += "\"pumpDurationSeconds\":" + String(pumpConfig.durationMs / 1000UL) + ",";
  json += "\"waterLowThreshold\":" + String(pumpConfig.lowLevelThreshold) + ",";
  json += "\"uptime\":\"" + formatDuration(millis() - state.bootMs) + "\",";
  json += "\"networkMode\":\"" + escapeJson(state.networkMode) + "\",";
  json += "\"appUrl\":\"" + escapeJson(state.appUrl) + "\",";
  json += "\"lastAlert\":\"" + escapeJson(state.lastAlert) + "\",";
  json += "\"cloudSyncEnabled\":" + String(state.cloudSyncEnabled ? "true" : "false") + ",";
  json += "\"cloudConnected\":" + String(state.cloudConnected ? "true" : "false") + ",";
  json += "\"lastCloudSyncSeconds\":" + String(state.lastCloudSyncMs / 1000UL) + ",";
  json += "\"lastCloudStatus\":\"" + escapeJson(state.lastCloudStatus) + "\",";
  json += "\"cameraIntegrationReady\":" + String(CAMERA_INTEGRATION_READY ? "true" : "false") + ",";
  json += "\"picoLastMessage\":\"" + escapeJson(picoState.lastMessage) + "\"";
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
  <title>Pet Guardian Hub</title>
  <style>
    :root {
      --bg: #0f172a;
      --panel: rgba(255, 255, 255, 0.08);
      --panel-strong: rgba(255, 255, 255, 0.12);
      --line: rgba(255, 255, 255, 0.16);
      --text: #f8fafc;
      --muted: #cbd5e1;
      --shadow: 0 18px 45px rgba(0, 0, 0, 0.28);
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      font-family: "Segoe UI", Tahoma, Geneva, Verdana, sans-serif;
      color: var(--text);
      background: linear-gradient(160deg, #0b1120 0%, #101827 52%, #172036 100%);
    }
    .shell { max-width: 1320px; margin: 0 auto; padding: 24px; }
    .hero { display: grid; grid-template-columns: 1.4fr 0.9fr; gap: 20px; margin-bottom: 22px; }
    .panel { background: var(--panel); border: 1px solid var(--line); border-radius: 24px; box-shadow: var(--shadow); }
    .hero-main { padding: 28px; }
    h1 { margin: 0 0 10px; font-size: clamp(2rem, 4vw, 3.5rem); line-height: 1; }
    .tagline { margin: 0; max-width: 760px; color: var(--muted); font-size: 1rem; line-height: 1.6; }
    .hero-meta { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 18px; }
    .chip { padding: 10px 14px; border-radius: 999px; background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.14); color: var(--muted); }
    .hero-side { padding: 22px; display: grid; gap: 14px; }
    .signal { padding: 16px; border-radius: 18px; background: var(--panel-strong); border: 1px solid var(--line); }
    .signal strong { display: block; font-size: 0.85rem; text-transform: uppercase; color: var(--muted); margin-bottom: 10px; }
    .signal span { font-size: 1.25rem; font-weight: 700; }
    .grid { display: grid; gap: 16px; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); margin-bottom: 20px; }
    .card { padding: 18px; border-radius: 20px; background: var(--panel); border: 1px solid var(--line); box-shadow: var(--shadow); }
    .label { color: var(--muted); font-size: 0.84rem; text-transform: uppercase; margin-bottom: 8px; }
    .value { font-size: 2rem; font-weight: 700; }
    .subvalue { margin-top: 8px; color: var(--muted); font-size: 0.95rem; }
    .layout { display: grid; gap: 18px; grid-template-columns: 1.1fr 0.9fr; }
    .stack { display: grid; gap: 18px; }
    .section { padding: 22px; }
    .section h2 { margin: 0 0 16px; font-size: 1.1rem; text-transform: uppercase; }
    .actions { display: grid; gap: 12px; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); }
    button { border: 0; border-radius: 14px; padding: 14px 16px; font-size: 1rem; font-weight: 700; cursor: pointer; color: #08111f; background: linear-gradient(135deg, #ecfeff 0%, #a5f3fc 100%); }
    button.primary { background: linear-gradient(135deg, #86efac 0%, #22c55e 100%); }
    button.secondary { background: linear-gradient(135deg, #bfdbfe 0%, #38bdf8 100%); }
    button.warn { background: linear-gradient(135deg, #fde68a 0%, #f59e0b 100%); }
    button.danger { background: linear-gradient(135deg, #fecaca 0%, #ef4444 100%); }
    .form-grid { display: grid; gap: 14px; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); }
    label { display: block; color: var(--muted); font-size: 0.95rem; }
    input[type="number"] { width: 100%; margin-top: 8px; padding: 12px 14px; border-radius: 14px; border: 1px solid var(--line); background: rgba(7,12,24,0.46); color: var(--text); font-size: 1rem; }
    input[type="checkbox"] { transform: scale(1.25); margin-right: 10px; }
    .footer-note { margin-top: 18px; color: var(--muted); font-size: 0.93rem; line-height: 1.6; }
    @media (max-width: 980px) { .hero, .layout { grid-template-columns: 1fr; } }
  </style>
</head>
<body>
  <div class="shell">
    <section class="hero">
      <article class="panel hero-main">
        <h1>Pet Guardian Hub</h1>
        <p class="tagline">Hub principal no ESP32 com comedouro no canal A da ponte H e bomba d'agua no canal B da ponte H.</p>
        <div class="hero-meta">
          <span class="chip" id="networkModeChip">Modo de rede: --</span>
          <span class="chip" id="appUrlChip">App: --</span>
          <span class="chip" id="cameraChip">Camera pronta: sim</span>
        </div>
      </article>
      <aside class="panel hero-side">
        <div class="signal"><strong>BitDogLab</strong><span id="picoStatus">--</span></div>
        <div class="signal"><strong>Ultimo alerta</strong><span id="lastAlert">--</span></div>
        <div class="signal"><strong>Tempo ligado</strong><span id="uptime">--</span></div>
        <div class="signal"><strong>Nuvem</strong><span id="cloudStatus">--</span></div>
      </aside>
    </section>

    <section class="grid">
      <article class="card"><div class="label">Temperatura</div><div class="value" id="temperature">--</div><div class="subvalue">DHT11</div></article>
      <article class="card"><div class="label">Umidade</div><div class="value" id="humidity">--</div><div class="subvalue">DHT11</div></article>
      <article class="card"><div class="label">Racao</div><div class="value" id="food">--</div><div class="subvalue">Ultrassonico 1</div></article>
      <article class="card"><div class="label">Agua</div><div class="value" id="water">--</div><div class="subvalue">Ultrassonico 2</div></article>
      <article class="card"><div class="label">Gas</div><div class="value" id="gas">--</div><div class="subvalue">Sensor analogico</div></article>
      <article class="card"><div class="label">Luminosidade</div><div class="value" id="light">--</div><div class="subvalue" id="lightState">--</div></article>
      <article class="card"><div class="label">Presenca</div><div class="value" id="motion">--</div><div class="subvalue">PIR</div></article>
      <article class="card"><div class="label">Bomba</div><div class="value" id="pumpState">--</div><div class="subvalue">Canal B da ponte H</div></article>
    </section>

    <section class="layout">
      <div class="stack">
        <article class="panel section">
          <h2>Acoes Rapidas</h2>
          <div class="actions">
            <button class="primary" onclick="postAction('/api/feed')">Acionar comedouro</button>
            <button class="secondary" onclick="postAction('/api/pump?state=run')">Acionar bomba</button>
            <button class="secondary" onclick="postAction('/api/lamp?state=on')">Ligar lampada</button>
            <button class="danger" onclick="postAction('/api/lamp?state=off')">Desligar lampada</button>
            <button class="warn" onclick="postAction('/api/lamp?state=auto')">Lampada automatica</button>
            <button class="warn" onclick="postAction('/api/pump?state=auto')">Bomba automatica</button>
          </div>
        </article>

        <article class="panel section">
          <h2>Iluminacao BitDogLab</h2>
          <div class="form-grid">
            <label><input type="checkbox" id="autoLampEnabled">Ativar automacao por escuridao + presenca</label>
            <label>Limite de escuridao<input id="darkThreshold" type="number" min="0" max="4095"></label>
            <label>Duracao da lampada (segundos)<input id="lampDurationSeconds" type="number" min="5" max="3600"></label>
            <label>Brilho da matriz (1 a 10)<input id="lampBrightness" type="number" min="1" max="10"></label>
          </div>
          <div class="actions" style="margin-top:16px;"><button class="primary" onclick="saveLampConfig()">Salvar lampada</button></div>
        </article>
      </div>

      <div class="stack">
        <article class="panel section">
          <h2>Fluxos Automaticos</h2>
          <div class="form-grid">
            <label><input type="checkbox" id="feedAutoEnabled">Alimentacao automatica</label>
            <label>Intervalo da racao (horas)<input id="feedIntervalHours" type="number" min="1" max="24"></label>
            <label><input type="checkbox" id="pumpAutoEnabled">Reposicao automatica de agua</label>
            <label>Tempo da bomba (segundos)<input id="pumpDurationSeconds" type="number" min="1" max="120"></label>
            <label>Nivel baixo da agua (%)<input id="waterLowThreshold" type="number" min="1" max="100"></label>
          </div>
          <div class="actions" style="margin-top:16px;"><button class="primary" onclick="saveAutomationConfig()">Salvar automacao</button></div>
        </article>

        <article class="panel section">
          <h2>Integracao Futura</h2>
          <p class="footer-note">O sistema ja esta preparado para receber a ESP32-CAM depois, sem quebrar a arquitetura atual.</p>
          <p class="footer-note" id="picoLastMessage">Estado da BitDogLab: --</p>
        </article>
      </div>
    </section>
  </div>

  <script>
    async function fetchStatus() {
      const response = await fetch('/api/status');
      return response.json();
    }

    function boolText(value, onText, offText) {
      return value ? onText : offText;
    }

    async function refreshStatus() {
      const data = await fetchStatus();
      document.getElementById('temperature').textContent = `${data.temperatureC} C`;
      document.getElementById('humidity').textContent = `${data.humidity} %`;
      document.getElementById('food').textContent = `${data.foodLevelPercent} %`;
      document.getElementById('water').textContent = `${data.waterLevelPercent} %`;
      document.getElementById('gas').textContent = data.gasRaw;
      document.getElementById('light').textContent = data.lightRaw;
      document.getElementById('lightState').textContent = data.isDark ? 'Ambiente escuro' : 'Ambiente claro';
      document.getElementById('motion').textContent = data.motionDetected ? 'Detectada' : 'Sem movimento';
      document.getElementById('pumpState').textContent = data.pumpOn ? 'Ligada' : 'Desligada';
      document.getElementById('networkModeChip').textContent = `Modo de rede: ${data.networkMode}`;
      document.getElementById('appUrlChip').textContent = `App: ${data.appUrl}`;
      document.getElementById('cameraChip').textContent = `Camera pronta: ${data.cameraIntegrationReady ? 'sim' : 'nao'}`;
      document.getElementById('picoStatus').textContent = `Sinal digital | Lampada ${boolText(data.lampOn, 'ligada', 'desligada')}`;
      document.getElementById('lastAlert').textContent = data.lastAlert;
      document.getElementById('uptime').textContent = data.uptime;
      document.getElementById('picoLastMessage').textContent = `Estado da BitDogLab: ${data.picoLastMessage}`;
      document.getElementById('cloudStatus').textContent = data.cloudSyncEnabled
        ? `${data.cloudConnected ? 'Conectada' : 'Falha'} | ${data.lastCloudStatus}`
        : 'Desativada';

      document.getElementById('autoLampEnabled').checked = data.autoLampEnabled;
      document.getElementById('darkThreshold').value = data.darkThreshold;
      document.getElementById('lampDurationSeconds').value = data.lampDurationSeconds;
      document.getElementById('lampBrightness').value = data.lampBrightness;

      document.getElementById('feedAutoEnabled').checked = data.feedAutoEnabled;
      document.getElementById('feedIntervalHours').value = data.feedIntervalHours;
      document.getElementById('pumpAutoEnabled').checked = data.pumpAutoEnabled;
      document.getElementById('pumpDurationSeconds').value = data.pumpDurationSeconds;
      document.getElementById('waterLowThreshold').value = data.waterLowThreshold;
    }

    async function postAction(url) {
      await fetch(url, { method: 'POST' });
      refreshStatus();
    }

    async function saveLampConfig() {
      const body = new URLSearchParams({
        autoLampEnabled: document.getElementById('autoLampEnabled').checked ? 'true' : 'false',
        darkThreshold: document.getElementById('darkThreshold').value,
        lampDurationSeconds: document.getElementById('lampDurationSeconds').value,
        lampBrightness: document.getElementById('lampBrightness').value
      });

      await fetch('/api/config/lamp', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body
      });

      refreshStatus();
    }

    async function saveAutomationConfig() {
      const body = new URLSearchParams({
        feedAutoEnabled: document.getElementById('feedAutoEnabled').checked ? 'true' : 'false',
        feedIntervalHours: document.getElementById('feedIntervalHours').value,
        pumpAutoEnabled: document.getElementById('pumpAutoEnabled').checked ? 'true' : 'false',
        pumpDurationSeconds: document.getElementById('pumpDurationSeconds').value,
        waterLowThreshold: document.getElementById('waterLowThreshold').value
      });

      await fetch('/api/config/automation', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body
      });

      refreshStatus();
    }

    refreshStatus();
    setInterval(refreshStatus, 2500);
  </script>
</body>
</html>
)rawliteral";
}

void handleRoot() {
  server.send(200, "text/html; charset=utf-8", htmlPage());
}

void handleStatus() {
  server.send(200, "application/json", buildStatusJson());
}

void handleFeed() {
  dispenseFood();
  server.send(200, "application/json", "{\"ok\":true}");
}

void handlePump() {
  String stateParam = server.arg("state");

  // A bomba pode ser acionada por tempo fixo ou devolvida ao controle automatico.
  if (stateParam == "run") {
    state.manualPumpMode = true;
    setPumpForDuration(pumpConfig.durationMs);
  } else if (stateParam == "auto") {
    state.manualPumpMode = false;
  } else {
    server.send(400, "application/json", "{\"ok\":false,\"message\":\"Use state=run ou state=auto\"}");
    return;
  }

  server.send(200, "application/json", "{\"ok\":true}");
}

void handleLamp() {
  String stateParam = server.arg("state");

  // Comandos manuais bloqueiam a automacao ate o usuario escolher o modo auto novamente.
  if (stateParam == "on") {
    picoLampOn(true);
  } else if (stateParam == "off") {
    picoLampOff(true);
  } else if (stateParam == "auto") {
    picoState.manualMode = false;
    evaluateAutomation();
  } else {
    server.send(400, "application/json", "{\"ok\":false,\"message\":\"Use state=on, off ou auto\"}");
    return;
  }

  server.send(200, "application/json", "{\"ok\":true}");
}

void handleLampConfig() {
  if (server.hasArg("autoLampEnabled")) lampConfig.autoEnabled = server.arg("autoLampEnabled") == "true";
  if (server.hasArg("darkThreshold")) lampConfig.darkThreshold = constrain(server.arg("darkThreshold").toInt(), 0, 4095);
  if (server.hasArg("lampDurationSeconds")) {
    unsigned long seconds = static_cast<unsigned long>(server.arg("lampDurationSeconds").toInt());
    lampConfig.durationMs = constrain(seconds, 5UL, 3600UL) * 1000UL;
  }
  if (server.hasArg("lampBrightness")) {
    lampConfig.brightness = constrain(server.arg("lampBrightness").toInt(), 1, 10);
    picoState.brightness = lampConfig.brightness;
  }

  saveConfig();
  server.send(200, "application/json", "{\"ok\":true}");
}

void handleAutomationConfig() {
  if (server.hasArg("feedAutoEnabled")) feedConfig.autoEnabled = server.arg("feedAutoEnabled") == "true";
  if (server.hasArg("feedIntervalHours")) {
    unsigned long hours = static_cast<unsigned long>(server.arg("feedIntervalHours").toInt());
    hours = constrain(hours, 1UL, 24UL);
    feedConfig.intervalMs = hours * 3600000UL;
  }
  if (server.hasArg("pumpAutoEnabled")) pumpConfig.autoEnabled = server.arg("pumpAutoEnabled") == "true";
  if (server.hasArg("pumpDurationSeconds")) {
    unsigned long seconds = static_cast<unsigned long>(server.arg("pumpDurationSeconds").toInt());
    seconds = constrain(seconds, 1UL, 120UL);
    pumpConfig.durationMs = seconds * 1000UL;
  }
  if (server.hasArg("waterLowThreshold")) pumpConfig.lowLevelThreshold = constrain(server.arg("waterLowThreshold").toInt(), 1, 100);

  saveConfig();
  server.send(200, "application/json", "{\"ok\":true}");
}

void startSoftAp(bool keepStationEnabled = false) {
  WiFi.mode(keepStationEnabled ? WIFI_AP_STA : WIFI_AP);
  WiFi.softAPConfig(apIp, apGateway, apSubnet);
  WiFi.softAP(AP_SSID, AP_PASSWORD);

  fallbackAccessPointActive = true;
  state.networkMode = keepStationEnabled ? "Access Point + reconexao Wi-Fi" : "Access Point";
  state.appUrl = "http://" + WiFi.softAPIP().toString();
}

void handleWiFiConnected() {
  state.networkMode = fallbackAccessPointActive ? "Cliente Wi-Fi + Access Point" : "Cliente Wi-Fi";
  state.appUrl = "http://" + WiFi.localIP().toString();
  state.lastCloudStatus = CLOUD_SYNC_ENABLED ? "Wi-Fi conectado; aguardando envio" : "Nuvem desativada";
  Serial.println("[WIFI] Conectado com sucesso.");
  Serial.println("[WIFI] IP local: " + WiFi.localIP().toString());
  Serial.println("[WIFI] Sinal RSSI: " + String(WiFi.RSSI()) + " dBm");
  if (!wifiWasConnected) {
    wifiWasConnected = true;
    playInternetConnectedMelody();
  }
}

void connectToWiFi() {
  if (USE_SOFT_AP) {
    startSoftAp();
    return;
  }

  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.setAutoReconnect(true);
  WiFi.persistent(false);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  unsigned long startAttemptMs = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - startAttemptMs < 15000UL) {
    delay(500);
  }

  if (WiFi.status() == WL_CONNECTED) {
    handleWiFiConnected();
    return;
  }

  // Se o roteador nao responder, o hub abre um AP local e segue tentando voltar ao Wi-Fi.
  Serial.println("[WIFI] Falha inicial. Access Point ativo; novas tentativas continuarao.");
  startSoftAp(true);
  WiFi.setSleep(false);
  WiFi.setAutoReconnect(true);
  lastWiFiRetryMs = millis();
  lastWiFiFullRestartMs = millis();
}

void maintainWiFiConnection() {
  if (USE_SOFT_AP) return;
  if (WiFi.status() == WL_CONNECTED) {
    if (!wifiWasConnected) handleWiFiConnected();
    return;
  }

  if (wifiWasConnected) {
    wifiWasConnected = false;
    state.cloudConnected = false;
    state.lastCloudStatus = "Wi-Fi desconectado";
    Serial.println("[WIFI] Conexao perdida.");
  }

  unsigned long now = millis();
  if (now - lastWiFiRetryMs < WIFI_RETRY_INTERVAL_MS) return;
  lastWiFiRetryMs = now;
  Serial.println("[WIFI] Aguardando reconexao em: " + String(WIFI_SSID) + " (" + wifiStatusLabel(WiFi.status()) + ")");

  if (now - lastWiFiFullRestartMs >= WIFI_FULL_RESTART_INTERVAL_MS) {
    lastWiFiFullRestartMs = now;
    Serial.println("[WIFI] Reiniciando interface Wi-Fi apos desconexao prolongada.");
    WiFi.disconnect(false, false);
    WiFi.mode(fallbackAccessPointActive ? WIFI_AP_STA : WIFI_STA);
    WiFi.setSleep(false);
    WiFi.setAutoReconnect(true);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    return;
  }

}

void setupPins() {
  pinMode(TRIG_FOOD_PIN, OUTPUT);
  pinMode(ECHO_FOOD_PIN, INPUT);
  pinMode(TRIG_WATER_PIN, OUTPUT);
  pinMode(ECHO_WATER_PIN, INPUT);
  pinMode(PIR_PIN, INPUT);
  pinMode(GAS_PIN, INPUT);
  pinMode(LDR_PIN, INPUT);
  pinMode(BITDOGLAB_LAMP_SIGNAL_PIN, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);

  pinMode(FEED_IN1_PIN, OUTPUT);
  pinMode(FEED_IN2_PIN, OUTPUT);
  pinMode(FEED_EN_PIN, OUTPUT);

  pinMode(PUMP_IN3_PIN, OUTPUT);
  pinMode(PUMP_IN4_PIN, OUTPUT);
  pinMode(PUMP_EN_PIN, OUTPUT);

  digitalWrite(BITDOGLAB_LAMP_SIGNAL_PIN, LOW);
  digitalWrite(BUZZER_PIN, LOW);
  setFeedMotor(false);
  setPump(false);
}

void setupServer() {
  server.on("/", HTTP_GET, handleRoot);
  server.on("/api/status", HTTP_GET, handleStatus);
  server.on("/api/feed", HTTP_POST, handleFeed);
  server.on("/api/pump", HTTP_POST, handlePump);
  server.on("/api/lamp", HTTP_POST, handleLamp);
  server.on("/api/config/lamp", HTTP_POST, handleLampConfig);
  server.on("/api/config/automation", HTTP_POST, handleAutomationConfig);
  server.begin();
}

void setup() {
  Serial.begin(115200);
  delay(400);

  state.bootMs = millis();
  loadConfig();
  picoState.brightness = lampConfig.brightness;
  dht.begin();
  setupPins();
  connectToWiFi();
  readSensors();
  setupServer();
}

void loop() {
  server.handleClient();
  maintainWiFiConnection();

  unsigned long now = millis();
  if (now - lastSensorReadMs >= SENSOR_INTERVAL_MS) {
    lastSensorReadMs = now;
    readSensors();
  }

  if (now - lastCloudAttemptMs >= CLOUD_SYNC_INTERVAL_MS) {
    lastCloudAttemptMs = now;
    syncToCloud();
  }
}
