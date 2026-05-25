const config = window.PET_DASHBOARD_CONFIG ?? {};
const storageKey = "pet-guardian-theme";
const state = {
  snapshot: null,
  history: [],
  device: null,
  activeTheme: localStorage.getItem(storageKey) || config.defaultTheme || "blossom"
};

const themes = [
  {
    id: "blossom",
    name: "Blossom",
    colors: ["#f5a7c1", "#ffdbe7", "#b6e6d3"]
  },
  {
    id: "mint",
    name: "Mint",
    colors: ["#9adbb9", "#e7f9ef", "#f7d8e4"]
  },
  {
    id: "butter",
    name: "Butter",
    colors: ["#f5c874", "#fff3cf", "#cfe9da"]
  },
  {
    id: "cocoa",
    name: "Cocoa",
    colors: ["#dfac8f", "#fbe8dd", "#d7ebc8"]
  }
];

const demoPayload = {
  device: { name: "Pet Hub Casa", hardware_type: "esp32 + pico w" },
  snapshot: {
    created_at: new Date().toISOString(),
    temperature_c: 27.4,
    humidity: 62,
    food_level_percent: 61,
    water_level_percent: 48,
    gas_raw: 312,
    light_raw: 1450,
    is_dark: true,
    motion_detected: true,
    pump_on: false,
    lamp_on: true,
    feed_motor_on: false,
    alert_text: "Tudo sob controle"
  },
  history: Array.from({ length: 10 }).map((_, index) => ({
    created_at: new Date(Date.now() - index * 1000 * 60 * 18).toISOString(),
    temperature_c: 26 + Math.random() * 2.5,
    humidity: 58 + Math.random() * 8,
    food_level_percent: 70 - index * 2,
    water_level_percent: 56 - index,
    gas_raw: 250 + Math.round(Math.random() * 80),
    light_raw: 1200 + Math.round(Math.random() * 900),
    is_dark: index < 4,
    motion_detected: index % 2 === 0,
    pump_on: false,
    lamp_on: index < 3,
    feed_motor_on: index === 5,
    alert_text: index === 6 ? "Racao entrando em zona de atencao" : ""
  }))
};

const el = (id) => document.getElementById(id);
const body = document.body;

function clampPercent(value) {
  return Math.max(0, Math.min(100, Number(value) || 0));
}

function asPercent(value) {
  return `${clampPercent(value)}%`;
}

function asYesNo(value, yes = "Sim", no = "Nao") {
  return value ? yes : no;
}

function fmtDate(value) {
  return new Date(value).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}

function buildMiniChart(values) {
  const safe = values.length ? values : [40, 58, 35, 72, 64, 48];
  const max = Math.max(...safe, 1);
  return safe.map((value) => `<span style="height:${Math.max(18, (value / max) * 100)}%"></span>`).join("");
}

function comfortLabel(snapshot) {
  if (!snapshot) return "Aguardando";
  if ((snapshot.temperature_c ?? 0) >= 31 || (snapshot.gas_raw ?? 0) >= 1800) return "Atencao";
  if (snapshot.is_dark && snapshot.motion_detected) return "Vigiando";
  return "Estavel";
}

function computeInsights(snapshot) {
  const insights = [];
  if (!snapshot) {
    return ["Sem dados ainda. Assim que o ESP32 sincronizar, os indicadores aparecem aqui."];
  }

  if ((snapshot.temperature_c ?? 0) >= 31) {
    insights.push("Temperatura acima do ideal. Vale rever ventilacao ou o ponto em que o pet descansa.");
  } else {
    insights.push("Conforto termico dentro de uma faixa tranquila para acompanhamento diario.");
  }

  if ((snapshot.water_level_percent ?? 0) <= 25) {
    insights.push("Nivel de agua em zona de alerta. Se a automacao estiver ativa, a bomba deve entrar em seguida.");
  } else {
    insights.push("Reserva de agua ainda saudavel para curto prazo.");
  }

  if ((snapshot.food_level_percent ?? 0) <= 25) {
    insights.push("Racao com nivel baixo. Ja vale pensar em reposicao antes do proximo ciclo automatico.");
  }

  if (snapshot.is_dark && snapshot.motion_detected) {
    insights.push("Ambiente escuro com presenca detectada: a regra da lampada faz bastante sentido nesse momento.");
  }

  if ((snapshot.gas_raw ?? 0) >= 1800) {
    insights.push("Leitura de gas acima do limite configurado. Esse e um evento critico e merece resposta imediata.");
  }

  if (!insights.length) {
    insights.push("Sistema estavel. Continue acompanhando o historico e os padroes de consumo do pet.");
  }

  return insights;
}

function setTheme(themeId) {
  state.activeTheme = themeId;
  body.dataset.theme = themeId;
  localStorage.setItem(storageKey, themeId);
  renderThemeSelector();
}

function renderThemeSelector() {
  const container = el("themeSelector");
  container.innerHTML = themes.map((theme) => `
    <button class="theme-button${theme.id === state.activeTheme ? " is-active" : ""}" data-theme="${theme.id}" type="button" aria-pressed="${theme.id === state.activeTheme}">
      <div class="theme-swatches">
        ${theme.colors.map((color) => `<span style="background:${color}"></span>`).join("")}
      </div>
      <span class="theme-label">${theme.name}</span>
    </button>
  `).join("");

  container.querySelectorAll(".theme-button").forEach((button) => {
    button.addEventListener("click", () => setTheme(button.dataset.theme));
  });
}

function render(payload, source = "cloud") {
  state.device = payload.device;
  state.snapshot = payload.snapshot;
  state.history = payload.history ?? [];

  const snapshot = state.snapshot;
  const history = state.history;
  const alertText = snapshot?.alert_text?.trim();

  el("deviceName").textContent = payload.device?.name ?? "Pet Hub";
  el("deviceSubtitle").textContent = payload.device?.hardware_type ? payload.device.hardware_type.toUpperCase() : "ESP32 + PICO W";
  el("deviceType").textContent = (payload.device?.hardware_type ?? "esp32").toUpperCase();
  el("networkMode").textContent = source === "demo" ? "Demo local" : "Nuvem ativa";
  el("connectionBadge").textContent = source === "demo" ? "Modo demonstracao" : "Sincronizado";
  el("connectionBadge").className = `presence-pill${source === "demo" ? " warn" : ""}`;
  el("lastSyncLabel").textContent = snapshot?.created_at ? `Ultima leitura em ${fmtDate(snapshot.created_at)}` : "Sem sincronizacao recente";

  el("alertHeadline").textContent = alertText || "Tudo tranquilo";
  el("alertSupport").textContent = alertText ? "O hub registrou uma situacao de atencao." : "Sem anomalias recentes.";
  el("comfortTag").textContent = comfortLabel(snapshot);

  el("temperatureValue").textContent = snapshot?.temperature_c != null ? `${Number(snapshot.temperature_c).toFixed(1)} C` : "--";
  el("humidityValue").textContent = snapshot?.humidity != null ? `${Number(snapshot.humidity).toFixed(0)} %` : "--";
  el("foodValue").textContent = asPercent(snapshot?.food_level_percent);
  el("waterValue").textContent = asPercent(snapshot?.water_level_percent);
  el("foodBar").style.width = asPercent(snapshot?.food_level_percent);
  el("waterBar").style.width = asPercent(snapshot?.water_level_percent);
  el("lightValue").textContent = snapshot?.light_raw ?? "--";
  el("gasValue").textContent = snapshot?.gas_raw ?? "--";
  el("motionValue").textContent = asYesNo(snapshot?.motion_detected, "Ativa", "Calma");
  el("darkValue").textContent = asYesNo(snapshot?.is_dark, "Escuro", "Claro");
  el("lampState").textContent = asYesNo(snapshot?.lamp_on, "Ligada", "Dormindo");
  el("pumpState").textContent = asYesNo(snapshot?.pump_on, "Ligada", "Parada");
  el("feedState").textContent = asYesNo(snapshot?.feed_motor_on, "Rodando", "Parado");
  el("alertState").textContent = alertText ? "Atencao" : "Normal";
  el("comfortChart").innerHTML = buildMiniChart(history.slice(0, 8).reverse().map((item) => Number(item.temperature_c ?? 0) + Number(item.humidity ?? 0) / 4));

  el("timelineList").innerHTML = history.length
    ? history.slice(0, 10).map((item) => `
      <article class="timeline-item">
        <header>
          <span>${fmtDate(item.created_at)}</span>
          <span>${item.alert_text || "Sem alerta"}</span>
        </header>
        <div class="timeline-metrics">
          <span>${Number(item.temperature_c ?? 0).toFixed(1)} C</span>
          <span>${Number(item.humidity ?? 0).toFixed(0)} %</span>
          <span>Racao ${asPercent(item.food_level_percent)}</span>
          <span>Agua ${asPercent(item.water_level_percent)}</span>
          <span>${item.motion_detected ? "Presenca" : "Sem presenca"}</span>
        </div>
      </article>
    `).join("")
    : `<article class="timeline-item"><header><span>Sem historico</span><span>Aguardando eventos</span></header></article>`;

  el("insightList").innerHTML = computeInsights(snapshot)
    .map((text) => `<li><strong>Leitura</strong>${text}</li>`)
    .join("");
}

async function fetchCloudData() {
  if (config.demoMode) {
    render(demoPayload, "demo");
    return;
  }

  if (!config.apiBaseUrl || !config.dashboardToken) {
    render(demoPayload, "demo");
    el("lastSyncLabel").textContent = "Preencha config.js para sair do modo demonstracao.";
    return;
  }

  const endpoint = `${config.apiBaseUrl}?token=${encodeURIComponent(config.dashboardToken)}&limit=32`;
  const response = await fetch(endpoint, {
    headers: {
      "x-dashboard-token": config.dashboardToken
    }
  });

  if (!response.ok) {
    throw new Error(`Falha HTTP ${response.status}`);
  }

  const payload = await response.json();
  render(payload, "cloud");
}

async function refresh() {
  try {
    await fetchCloudData();
  } catch (error) {
    render(demoPayload, "demo");
    el("connectionBadge").textContent = "Sem nuvem";
    el("connectionBadge").className = "presence-pill warn";
    el("lastSyncLabel").textContent = `Erro: ${error.message}`;
  }
}

body.dataset.theme = state.activeTheme;
renderThemeSelector();
el("refreshButton").addEventListener("click", refresh);
refresh();
setInterval(refresh, Number(config.refreshIntervalMs || 15000));

if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => navigator.serviceWorker.register("./sw.js").catch(() => {}));
}