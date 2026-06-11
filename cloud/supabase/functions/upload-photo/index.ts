import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const BUCKET = "pet-photos";
const MAX_PHOTO_BYTES = 300_000;
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "content-type, x-device-token, x-command-id, x-photo-reason",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "method not allowed" }, 405);

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
    );
    const token = req.headers.get("x-device-token");
    if (!token) return json({ error: "missing token" }, 401);

    const { data: device, error: deviceError } = await supabase
      .from("devices")
      .select("id,name")
      .eq("token", token)
      .single();
    if (deviceError || !device) return json({ error: "invalid device" }, 403);

    const bytes = new Uint8Array(await req.arrayBuffer());
    // A OV7670 envia BMP; validar assinatura e tamanho evita gravar payload arbitrario.
    if (bytes.length < 54 || bytes.length > MAX_PHOTO_BYTES) {
      return json({ error: "invalid photo size" }, 400);
    }
    if (bytes[0] !== 0x42 || bytes[1] !== 0x4d) {
      return json({ error: "expected BMP image" }, 400);
    }

    const commandHeader = req.headers.get("x-command-id");
    const commandId = commandHeader && /^\d+$/.test(commandHeader) ? Number(commandHeader) : null;
    const reason = (req.headers.get("x-photo-reason") ?? "manual").slice(0, 80);
    const photoId = crypto.randomUUID();
    const objectPath = `${device.id}/${new Date().toISOString().slice(0, 10)}/${photoId}.bmp`;

    const { error: uploadError } = await supabase.storage
      .from(BUCKET)
      .upload(objectPath, bytes, { contentType: "image/bmp", upsert: false });
    if (uploadError) return json({ error: uploadError.message }, 500);

    const { data: photo, error: insertError } = await supabase
      .from("pet_photos")
      .insert({
        id: photoId,
        device_id: device.id,
        command_id: commandId,
        storage_path: objectPath,
        content_type: "image/bmp",
        size_bytes: bytes.length,
        reason,
      })
      .select("id,created_at")
      .single();
    if (insertError) {
      await supabase.storage.from(BUCKET).remove([objectPath]);
      return json({ error: insertError.message }, 500);
    }

    if (commandId !== null) {
      await supabase
        .from("device_commands")
        .update({ status: "photo_uploaded" })
        .eq("id", commandId)
        .eq("device_id", device.id);
    }

    return json({ ok: true, photoId: photo.id, createdAt: photo.created_at, device: device.name }, 201);
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
