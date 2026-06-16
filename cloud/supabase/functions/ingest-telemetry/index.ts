import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-device-token",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );

    const body = await req.json();
    const token = req.headers.get("x-device-token") ?? body.deviceToken;

    // Cada ESP32 publica apenas com o token do dispositivo cadastrado.
    if (!token) {
      return new Response(JSON.stringify({ error: "missing token" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { data: device, error: deviceError } = await supabase
      .from("devices")
      .select("id,name")
      .eq("token", token)
      .single();

    if (deviceError || !device) {
      return new Response(JSON.stringify({ error: "invalid device" }), {
        status: 403,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const payload = {
      device_id: device.id,
      temperature_c: body.temperatureC ?? null,
      humidity: body.humidity ?? null,
      food_level_percent: body.foodLevelPercent ?? null,
      water_level_percent: body.waterLevelPercent ?? null,
      gas_raw: body.gasRaw ?? null,
      light_raw: body.lightRaw ?? null,
      is_dark: body.isDark ?? null,
      motion_detected: body.motionDetected ?? null,
      pump_on: body.pumpOn ?? null,
      lamp_on: body.lampOn ?? null,
      feed_motor_on: body.feedMotorOn ?? null,
      alert_text: body.lastAlert ?? null,
      payload: body,
    };

    // A tabela guarda campos normalizados e tambem o payload completo para auditoria.
    const { error: insertError } = await supabase.from("telemetry_events").insert(payload);

    if (insertError) {
      return new Response(JSON.stringify({ error: insertError.message }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    return new Response(JSON.stringify({ ok: true, device: device.name }), {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (error) {
    return new Response(JSON.stringify({ error: String(error) }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
