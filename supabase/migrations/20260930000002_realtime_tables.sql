-- Add messages table to Supabase Realtime publication
ALTER PUBLICATION supabase_realtime ADD TABLE public.messages;

-- Also add conversations for real-time conversation updates
ALTER PUBLICATION supabase_realtime ADD TABLE public.conversations;