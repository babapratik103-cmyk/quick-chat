-- Quick Chat MVP Schema Migration
-- Version: 1
-- Description: Initial schema for encrypted ephemeral 1-to-1 messaging

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "citext";

-- ============================================================
-- 1. profiles table
-- ============================================================
CREATE TABLE public.profiles (
    id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    username citext NOT NULL,
    name text NOT NULL,
    age integer NOT NULL CHECK (age > 0),
    public_key text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Username is globally unique and immutable
CREATE UNIQUE INDEX profiles_username_unique ON public.profiles (username);

-- ============================================================
-- 2. conversations table
-- ============================================================
CREATE TABLE public.conversations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    member_a uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    member_b uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT conversations_member_order CHECK (member_a < member_b),
    CONSTRAINT conversations_unique_pair UNIQUE (member_a, member_b)
);

-- ============================================================
-- 3. conversation_members table
-- ============================================================
CREATE TABLE public.conversation_members (
    conversation_id uuid NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    joined_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, user_id)
);

CREATE INDEX conversation_members_user_id_idx ON public.conversation_members (user_id);

-- ============================================================
-- 4. messages table
-- ============================================================
CREATE TABLE public.messages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    sender_id uuid NOT NULL,
    recipient_id uuid NOT NULL,
    ciphertext text NOT NULL,
    iv text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL DEFAULT (now() + interval '7 days'),
    CONSTRAINT messages_no_self_send CHECK (sender_id <> recipient_id),
    CONSTRAINT messages_sender_member FOREIGN KEY (conversation_id, sender_id)
        REFERENCES public.conversation_members (conversation_id, user_id) ON DELETE CASCADE,
    CONSTRAINT messages_recipient_member FOREIGN KEY (conversation_id, recipient_id)
        REFERENCES public.conversation_members (conversation_id, user_id) ON DELETE CASCADE
);

-- Indexes for message delivery and TTL purge
CREATE INDEX messages_recipient_created_idx ON public.messages (recipient_id, created_at);
CREATE INDEX messages_expires_at_idx ON public.messages (expires_at);
CREATE INDEX messages_conversation_created_idx ON public.messages (conversation_id, created_at);

-- ============================================================
-- 5. Profile trigger (atomic registration)
-- ============================================================
CREATE OR REPLACE FUNCTION public.on_auth_user_created()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    INSERT INTO public.profiles (id, username, name, age, public_key)
    VALUES (
        NEW.id,
        NEW.raw_user_meta_data->>'username',
        NEW.raw_user_meta_data->>'name',
        (NEW.raw_user_meta_data->>'age')::int,
        NEW.raw_user_meta_data->>'public_key'
    );
    RETURN NEW;
EXCEPTION
    WHEN unique_violation THEN
        RAISE EXCEPTION 'Username already exists';
    WHEN others THEN
        RAISE EXCEPTION 'Profile creation failed: %', SQLERRM;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION public.on_auth_user_created();

-- ============================================================
-- 6. Atomic conversation creation function
-- ============================================================
CREATE OR REPLACE FUNCTION public.create_conversation(peer_id uuid)
RETURNS public.conversations
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    my_id uuid := auth.uid();
    a_id uuid;
    b_id uuid;
    conv public.conversations;
BEGIN
    IF peer_id = my_id THEN
        RAISE EXCEPTION 'Cannot create conversation with yourself';
    END IF;

    -- Canonical ordering
    a_id := LEAST(my_id, peer_id);
    b_id := GREATEST(my_id, peer_id);

    -- Atomic insert with conflict handling
    INSERT INTO public.conversations (member_a, member_b)
    VALUES (a_id, b_id)
    ON CONFLICT (member_a, member_b) DO NOTHING
    RETURNING * INTO conv;

    -- If conflict, fetch existing
    IF conv IS NULL THEN
        SELECT * INTO conv
        FROM public.conversations
        WHERE member_a = a_id AND member_b = b_id;
    END IF;

    -- Sync conversation_members (idempotent)
    INSERT INTO public.conversation_members (conversation_id, user_id)
    VALUES (conv.id, my_id), (conv.id, peer_id)
    ON CONFLICT (conversation_id, user_id) DO NOTHING;

    RETURN conv;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.create_conversation(uuid) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.create_conversation(uuid) TO authenticated;

-- ============================================================
-- 7. public_profiles view (privacy-safe discovery)
-- ============================================================
CREATE VIEW public.public_profiles AS
SELECT
    id,
    username,
    name,
    public_key,
    created_at
FROM public.profiles;

REVOKE ALL ON public.public_profiles FROM anon, public;
GRANT SELECT ON public.public_profiles TO authenticated;

-- ============================================================
-- 8. RLS Policies
-- ============================================================

-- Enable RLS on all tables
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversation_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;

-- profiles: user reads/writes own private row; public discovery via view only
CREATE POLICY profiles_own_select ON public.profiles
    FOR SELECT TO authenticated
    USING (auth.uid() = id);

CREATE POLICY profiles_own_insert ON public.profiles
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = id);

CREATE POLICY profiles_own_update ON public.profiles
    FOR UPDATE TO authenticated
    USING (auth.uid() = id)
    WITH CHECK (auth.uid() = id AND username = (SELECT username FROM public.profiles WHERE id = auth.uid()));

-- conversations: only members may read
CREATE POLICY conversations_member_select ON public.conversations
    FOR SELECT TO authenticated
    USING (auth.uid() IN (member_a, member_b));

-- conversation_members: only the member may read their membership
CREATE POLICY conversation_members_own_select ON public.conversation_members
    FOR SELECT TO authenticated
    USING (user_id = auth.uid());

-- messages: recipient can SELECT pending; sender can INSERT; recipient-only DELETE for ACK
CREATE POLICY messages_recipient_select ON public.messages
    FOR SELECT TO authenticated
    USING (recipient_id = auth.uid());

CREATE POLICY messages_sender_insert ON public.messages
    FOR INSERT TO authenticated
    WITH CHECK (
        sender_id = auth.uid()
        AND recipient_id <> sender_id
    );

CREATE POLICY messages_recipient_delete ON public.messages
    FOR DELETE TO authenticated
    USING (recipient_id = auth.uid());

-- ============================================================
-- 9. TTL: expires_at clamp trigger + pg_cron purge
-- ============================================================

-- Prevent clients from extending expiry beyond 7 days
CREATE OR REPLACE FUNCTION public.clamp_message_expiry()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    IF NEW.expires_at > (now() + interval '7 days') THEN
        NEW.expires_at := now() + interval '7 days';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS clamp_message_expiry ON public.messages;
CREATE TRIGGER clamp_message_expiry
    BEFORE INSERT ON public.messages
    FOR EACH ROW
    EXECUTE FUNCTION public.clamp_message_expiry();

-- pg_cron hourly purge (requires pg_cron extension enabled in Supabase dashboard)
-- SELECT cron.schedule('purge-expired-messages-hourly', '5 * * * *', 'DELETE FROM public.messages WHERE expires_at < now();');

-- ============================================================
-- 10. Realtime preparation
-- ============================================================
-- Messages table INSERTs will be delivered via Supabase Realtime
-- (Configure in Supabase Dashboard: Realtime -> Tables -> messages -> Enable)
-- delivered/read events are transient, non-persistent (no schema changes needed)

-- ============================================================
-- 11. Cryptography: schema enforces required fields only
-- ============================================================
-- profiles.public_key = P-256 public key (base64, SEC1 uncompressed)
-- messages.ciphertext = AES-256-GCM ciphertext (base64)
-- messages.iv = 12-byte nonce (base64)
-- No private-key columns, no plaintext message storage

-- ============================================================
-- 12. Security hardening: REVOKE default PUBLIC grants
-- ============================================================
REVOKE ALL ON public.profiles FROM anon, public;
REVOKE ALL ON public.conversations FROM anon, public;
REVOKE ALL ON public.conversation_members FROM anon, public;
REVOKE ALL ON public.messages FROM anon, public;

GRANT SELECT, INSERT, UPDATE, DELETE ON public.profiles TO authenticated;
GRANT SELECT ON public.profiles TO service_role;
GRANT SELECT ON public.conversations TO authenticated;
GRANT SELECT ON public.conversation_members TO authenticated;
GRANT SELECT, INSERT, DELETE ON public.messages TO authenticated;