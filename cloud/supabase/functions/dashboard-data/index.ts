import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-dashboard-token",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
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

    const url = new URL(req.url);
    const dashboardToken = req.headers.get("x-dashboard-token") ?? url.searchParams.get("token");
    const limit = Number(url.searchParams.get("limit") ?? "24");

    if (!dashboardToken) {
      return new Response(JSON.stringify({ error: "missing dashboard token" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { data: device, error: deviceError } = await supabase
      .from("devices")
      .select("id,name,hardware_type,created_at")
      .eq("dashboard_token", dashboardToken)
      .single();

    if (deviceError || !device) {
      return new Response(JSON.stringify({ error: "invalid dashboard token" }), {
        status: 403,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { data: snapshot, error: snapshotError } = await supabase
      .from("latest_device_snapshot")
      .select("*")
      .eq("device_id", device.id)
      .maybeSingle();

    if (snapshotError) {
      return new Response(JSON.stringify({ error: snapshotError.message }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { data: history, error: historyError } = await supabase
      .from("telemetry_events")
      .select("created_at,temperature_c,humidity,food_level_percent,water_level_percent,gas_raw,light_raw,is_dark,motion_detected,pump_on,lamp_on,feed_motor_on,alert_text")
      .eq("device_id", device.id)
      .order("created_at", { ascending: false })
      .limit(Math.min(Math.max(limit, 1), 96));

    if (historyError) {
      return new Response(JSON.stringify({ error: historyError.message }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    return new Response(JSON.stringify({ device, snapshot, history }), {
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