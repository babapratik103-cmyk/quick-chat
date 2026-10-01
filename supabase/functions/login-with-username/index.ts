import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

interface LoginRequest {
  username: string
  password: string
}

interface LoginResponse {
  access_token: string
  refresh_token: string
  expires_in: number
  token_type: string
  user: {
    id: string
    email: string
  }
  email_confirmed_at: string | null
}

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
}

const GENERIC_ERROR = "Invalid username or password"

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
  const publishableKeys = JSON.parse(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS")!)
  const secretKeys = JSON.parse(Deno.env.get("SUPABASE_SECRET_KEYS")!)
  const supabaseAnonKey = publishableKeys.default
  const supabaseSecretKey = secretKeys.default
  
  if (!supabaseUrl || !supabaseAnonKey || !supabaseSecretKey) {
    return new Response(
      JSON.stringify({ error: "Server configuration error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )
  }

  let username: string
  let password: string

  try {
    const body = await req.json() as LoginRequest
    username = body.username?.trim().toLowerCase()
    password = body.password
  } catch {
    return new Response(
      JSON.stringify({ error: GENERIC_ERROR }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )
  }

  if (!username || !password) {
    return new Response(
      JSON.stringify({ error: GENERIC_ERROR }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )
  }

  const usernameRegex = /^[a-z0-9_]{3,20}$/
  if (!usernameRegex.test(username)) {
    return new Response(
      JSON.stringify({ error: GENERIC_ERROR }),
      { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )
  }

  // Use secret key (sb_secret_...) for privileged admin operations
  const adminClient = createClient(supabaseUrl, supabaseSecretKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  })

  const { data: profile, error: profileError } = await adminClient
    .from("profiles")
    .select("id")
    .eq("username", username)
    .maybeSingle()

  if (profileError || !profile) {
    return new Response(
      JSON.stringify({ error: GENERIC_ERROR }),
      { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )
  }

  const { data: authUser, error: userError } = await adminClient.auth.admin.getUserById(profile.id)

  if (userError || !authUser?.user?.email) {
    return new Response(
      JSON.stringify({ error: GENERIC_ERROR }),
      { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )
  }

  const realEmail = authUser.user.email

  // Use anon key (or publishable key) for password verification
  const authClient = createClient(supabaseUrl, supabaseAnonKey)
  const { data, error } = await authClient.auth.signInWithPassword({
    email: realEmail,
    password: password,
  })

  if (error || !data.session) {
    return new Response(
      JSON.stringify({ error: GENERIC_ERROR }),
      { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    )
  }

  const session = data.session

  const response: LoginResponse = {
    access_token: session.access_token,
    refresh_token: session.refresh_token,
    expires_in: session.expires_in,
    token_type: session.token_type,
    user: {
      id: session.user.id,
      email: session.user.email ?? realEmail,
    },
    email_confirmed_at: session.user.email_confirmed_at ?? null,
  }

  return new Response(JSON.stringify(response), {
    status: 200,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  })
})