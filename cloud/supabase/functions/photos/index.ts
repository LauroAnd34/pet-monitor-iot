import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const BUCKET = "pet-photos";
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "content-type, x-dashboard-token",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "GET") return json({ error: "method not allowed" }, 405);

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
    );
    const url = new URL(req.url);
    const token = req.headers.get("x-dashboard-token") ?? url.searchParams.get("token");
    if (!token) return json({ error: "missing dashboard token" }, 401);

    const { data: device, error: deviceError } = await supabase
      .from("devices")
      .select("id,name")
      .eq("dashboard_token", token)
      .single();
    if (deviceError || !device) return json({ error: "invalid dashboard token" }, 403);

    const requestedLimit = Number(url.searchParams.get("limit") ?? "40");
    const limit = Math.min(Math.max(Number.isFinite(requestedLimit) ? requestedLimit : 40, 1), 100);
    const after = url.searchParams.get("after");

    let query = supabase
      .from("pet_photos")
      .select("id,created_at,reason,content_type,size_bytes,storage_path,command_id")
      .eq("device_id", device.id)
      .order("created_at", { ascending: false })
      .limit(limit);
    if (after) query = query.gt("created_at", after);

    const { data: photos, error: photosError } = await query;
    if (photosError) return json({ error: photosError.message }, 500);

    const items = await Promise.all((photos ?? []).map(async (photo) => {
      const { data } = await supabase.storage.from(BUCKET).createSignedUrl(photo.storage_path, 900);
      return {
        id: photo.id,
        createdAt: photo.created_at,
        reason: photo.reason,
        contentType: photo.content_type,
        sizeBytes: photo.size_bytes,
        commandId: photo.command_id,
        downloadUrl: data?.signedUrl ?? null,
      };
    }));

    return json({ ok: true, device: device.name, photos: items });
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
