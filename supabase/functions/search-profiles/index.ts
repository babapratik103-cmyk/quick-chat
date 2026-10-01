import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

interface SearchRequest {
  query: string
}

interface SearchResponse {
  profiles: Profile[]
}

interface Profile {
  id: string
  username: string
  name: string
  public_key: string
  created_at: string
}

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
}

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders })
  }

  if (req.method !== "POST") {
    return new Response(
      JSON.stringify({ error: "Method not allowed" }),
      { status: 405, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!
  const secretKeys = JSON.parse(Deno.env.get("SUPABASE_SECRET_KEYS")!)
  const supabaseSecretKey = secretKeys.default

  if (!supabaseUrl || !supabaseSecretKey) {
    return new Response(
      JSON.stringify({ error: "Server configuration error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )
  }

  let query: string

  try {
    const body = await req.json() as SearchRequest
    query = body.query?.trim().toLowerCase() ?? ""
  } catch {
    return new Response(
      JSON.stringify({ error: "Invalid request" }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )
  }

  if (!query) {
    return new Response(
      JSON.stringify({ profiles: [] }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )
  }

  const usernameRegex = /^[a-z0-9_]{3,20}$/
  if (!usernameRegex.test(query)) {
    return new Response(
      JSON.stringify({ profiles: [] }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )
  }

  const adminClient = createClient(supabaseUrl, supabaseSecretKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  })

  const { data: profiles, error } = await adminClient
    .from("profiles")
    .select("id, username, name, public_key, created_at")
    .ilike("username", `%${query}%`)
    .limit(50)

  if (error) {
    return new Response(
      JSON.stringify({ error: "Search failed", details: error.message }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )
  }

  const response: SearchResponse = {
    profiles: profiles ?? [],
  }

  return new Response(JSON.stringify(response), {
    status: 200,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  })
})