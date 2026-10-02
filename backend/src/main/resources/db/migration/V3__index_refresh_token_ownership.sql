CREATE INDEX refresh_tokens_user_idx
    ON public.refresh_tokens (tenant_id, user_id);
