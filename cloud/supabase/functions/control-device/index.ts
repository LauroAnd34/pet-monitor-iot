import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-dashboard-token",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const allowedCommands = new Set(["feed_now", "pump_run", "pump_auto", "lamp_on", "lamp_off", "lamp_auto", "capture_photo"]);

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );

    const dashboardToken = req.headers.get("x-dashboard-token");
    if (!dashboardToken) {
      return new Response(JSON.stringify({ error: "missing dashboard token" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const body = await req.json();
    const commandType = String(body.commandType ?? "").trim();
    const payload = body.payload && typeof body.payload === "object" ? body.payload : {};

    // Lista fechada impede que o app injete comandos nao previstos no firmware.
    if (!allowedCommands.has(commandType)) {
      return new Response(JSON.stringify({ error: "invalid command" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { data: device, error: deviceError } = await supabase
      .from("devices")
      .select("id,name")
      .eq("dashboard_token", dashboardToken)
      .single();

    if (deviceError || !device) {
      return new Response(JSON.stringify({ error: "invalid dashboard token" }), {
        status: 403,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { error: insertError } = await supabase.from("device_commands").insert({
      device_id: device.id,
      command_type: commandType,
      payload,
      status: "pending",
    });

    if (insertError) {
      return new Response(JSON.stringify({ error: insertError.message }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    return new Response(JSON.stringify({ ok: true, message: `Comando ${commandType} enviado para ${device.name}.` }), {
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
