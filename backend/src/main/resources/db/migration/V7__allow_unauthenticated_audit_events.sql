ALTER TABLE public.audit_events
    ALTER COLUMN tenant_id DROP NOT NULL;

ALTER TABLE public.audit_events
    ADD CONSTRAINT audit_events_actor_requires_tenant_ck
        CHECK (actor_user_id IS NULL OR tenant_id IS NOT NULL);

COMMENT ON COLUMN public.audit_events.tenant_id IS
    'Authoritative tenant for authenticated actors; null only when no authenticated tenant is known.';

COMMENT ON COLUMN public.audit_events.actor_user_id IS
    'Authoritative authenticated actor; null for unauthenticated or system events.';
