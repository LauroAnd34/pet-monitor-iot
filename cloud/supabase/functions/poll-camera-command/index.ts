import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "content-type, x-device-token",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
    );
    const token = req.headers.get("x-device-token") ?? new URL(req.url).searchParams.get("token");
    if (!token) return json({ error: "missing token" }, 401);

    const { data: device, error: deviceError } = await supabase
      .from("devices")
      .select("id,name")
      .eq("token", token)
      .single();
    if (deviceError || !device) return json({ error: "invalid device" }, 403);

    const { data: command, error: commandError } = await supabase
      .from("device_commands")
      .select("id,command_type,payload,status,created_at")
      .eq("device_id", device.id)
      .eq("command_type", "capture_photo")
      .in("status", ["pending", "camera_processing"])
      .order("created_at", { ascending: true })
      .limit(1)
      .maybeSingle();
    if (commandError) return json({ error: commandError.message }, 500);
    if (!command) return json({ ok: true, commandType: null });

    // Mantem camera_processing elegivel para nova tentativa se a camera perder a internet.
    if (command.status === "pending") {
      const { error: updateError } = await supabase
        .from("device_commands")
        .update({ status: "camera_processing", consumed_at: new Date().toISOString() })
        .eq("id", command.id)
        .eq("status", "pending");
      if (updateError) return json({ error: updateError.message }, 500);
    }

    return json({
      ok: true,
      commandType: command.command_type,
      commandId: command.id,
      payload: command.payload ?? {},
      device: device.name,
    });
  } catch (error) {
    return json({ error: String(error) }, 500);
  }
});

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}
