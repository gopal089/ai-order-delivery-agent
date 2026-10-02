ALTER TABLE public.refresh_tokens
    ADD COLUMN session_id uuid;

UPDATE public.refresh_tokens
SET session_id = gen_random_uuid()
WHERE session_id IS NULL;

ALTER TABLE public.refresh_tokens
    ALTER COLUMN session_id SET NOT NULL;

COMMENT ON COLUMN public.refresh_tokens.session_id IS
    'Server-generated session family identifier used to rotate and revoke refresh tokens.';

CREATE INDEX refresh_tokens_session_idx
    ON public.refresh_tokens (tenant_id, user_id, session_id);
