CREATE TABLE public.users (
    id bigint GENERATED ALWAYS AS IDENTITY,
    tenant_id uuid NOT NULL DEFAULT gen_random_uuid(),
    email text NOT NULL,
    display_name text,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_pk PRIMARY KEY (id),
    CONSTRAINT users_tenant_user_uq UNIQUE (tenant_id, id),
    CONSTRAINT users_email_not_blank_ck CHECK (btrim(email) <> ''),
    CONSTRAINT users_display_name_not_blank_ck
        CHECK (display_name IS NULL OR btrim(display_name) <> ''),
    CONSTRAINT users_updated_at_ck CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX users_tenant_email_uq
    ON public.users (tenant_id, lower(email));

CREATE TABLE public.refresh_tokens (
    id bigint GENERATED ALWAYS AS IDENTITY,
    tenant_id uuid NOT NULL,
    user_id bigint NOT NULL,
    token_hash text NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT refresh_tokens_pk PRIMARY KEY (id),
    CONSTRAINT refresh_tokens_user_fk
        FOREIGN KEY (tenant_id, user_id)
        REFERENCES public.users (tenant_id, id),
    CONSTRAINT refresh_tokens_hash_uq UNIQUE (token_hash),
    CONSTRAINT refresh_tokens_hash_not_blank_ck CHECK (btrim(token_hash) <> ''),
    CONSTRAINT refresh_tokens_expiry_ck CHECK (expires_at > created_at),
    CONSTRAINT refresh_tokens_revoked_at_ck
        CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

COMMENT ON COLUMN public.refresh_tokens.token_hash IS
    'One-way hash of a refresh token; plaintext tokens must never be stored.';

CREATE INDEX refresh_tokens_active_user_expiry_idx
    ON public.refresh_tokens (tenant_id, user_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE public.integrations (
    id bigint GENERATED ALWAYS AS IDENTITY,
    tenant_id uuid NOT NULL,
    user_id bigint NOT NULL,
    provider_key text NOT NULL,
    display_name text NOT NULL,
    settings jsonb NOT NULL DEFAULT '{}'::jsonb,
    is_enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT integrations_pk PRIMARY KEY (id),
    CONSTRAINT integrations_tenant_user_id_uq UNIQUE (tenant_id, user_id, id),
    CONSTRAINT integrations_user_fk
        FOREIGN KEY (tenant_id, user_id)
        REFERENCES public.users (tenant_id, id),
    CONSTRAINT integrations_provider_not_blank_ck CHECK (btrim(provider_key) <> ''),
    CONSTRAINT integrations_name_not_blank_ck CHECK (btrim(display_name) <> ''),
    CONSTRAINT integrations_settings_object_ck CHECK (jsonb_typeof(settings) = 'object'),
    CONSTRAINT integrations_updated_at_ck CHECK (updated_at >= created_at),
    CONSTRAINT integrations_provider_name_uq
        UNIQUE (tenant_id, user_id, provider_key, display_name)
);

COMMENT ON COLUMN public.integrations.settings IS
    'Non-secret provider settings only. Credentials belong in an approved encrypted secret store.';

CREATE TABLE public.conversations (
    id bigint GENERATED ALWAYS AS IDENTITY,
    tenant_id uuid NOT NULL,
    user_id bigint NOT NULL,
    title text,
    status text NOT NULL DEFAULT 'active',
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT conversations_pk PRIMARY KEY (id),
    CONSTRAINT conversations_tenant_user_id_uq UNIQUE (tenant_id, user_id, id),
    CONSTRAINT conversations_user_fk
        FOREIGN KEY (tenant_id, user_id)
        REFERENCES public.users (tenant_id, id),
    CONSTRAINT conversations_title_not_blank_ck CHECK (title IS NULL OR btrim(title) <> ''),
    CONSTRAINT conversations_status_not_blank_ck CHECK (btrim(status) <> ''),
    CONSTRAINT conversations_updated_at_ck CHECK (updated_at >= created_at)
);

CREATE INDEX conversations_tenant_user_updated_idx
    ON public.conversations (tenant_id, user_id, updated_at DESC, id DESC);

CREATE TABLE public.messages (
    id bigint GENERATED ALWAYS AS IDENTITY,
    tenant_id uuid NOT NULL,
    user_id bigint NOT NULL,
    conversation_id bigint NOT NULL,
    message_role text NOT NULL,
    content text NOT NULL,
    model_name text,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT messages_pk PRIMARY KEY (id),
    CONSTRAINT messages_conversation_fk
        FOREIGN KEY (tenant_id, user_id, conversation_id)
        REFERENCES public.conversations (tenant_id, user_id, id),
    CONSTRAINT messages_role_not_blank_ck CHECK (btrim(message_role) <> ''),
    CONSTRAINT messages_content_not_blank_ck CHECK (btrim(content) <> ''),
    CONSTRAINT messages_model_not_blank_ck CHECK (model_name IS NULL OR btrim(model_name) <> '')
);

CREATE INDEX messages_conversation_created_idx
    ON public.messages (tenant_id, user_id, conversation_id, created_at, id);

CREATE TABLE public.orders (
    id bigint GENERATED ALWAYS AS IDENTITY,
    tenant_id uuid NOT NULL,
    user_id bigint NOT NULL,
    integration_id bigint NOT NULL,
    external_order_id text NOT NULL,
    order_status text,
    cached_payload jsonb,
    source_updated_at timestamptz,
    fetched_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cache_expires_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT orders_pk PRIMARY KEY (id),
    CONSTRAINT orders_tenant_user_id_uq UNIQUE (tenant_id, user_id, id),
    CONSTRAINT orders_integration_fk
        FOREIGN KEY (tenant_id, user_id, integration_id)
        REFERENCES public.integrations (tenant_id, user_id, id),
    CONSTRAINT orders_external_id_not_blank_ck CHECK (btrim(external_order_id) <> ''),
    CONSTRAINT orders_status_not_blank_ck
        CHECK (order_status IS NULL OR btrim(order_status) <> ''),
    CONSTRAINT orders_cached_payload_object_ck
        CHECK (cached_payload IS NULL OR jsonb_typeof(cached_payload) = 'object'),
    CONSTRAINT orders_cache_pair_ck CHECK (
        (cached_payload IS NULL AND cache_expires_at IS NULL)
        OR (cached_payload IS NOT NULL AND cache_expires_at IS NOT NULL)
    ),
    CONSTRAINT orders_cache_expiry_ck
        CHECK (cache_expires_at IS NULL OR cache_expires_at > fetched_at),
    CONSTRAINT orders_updated_at_ck CHECK (updated_at >= created_at),
    CONSTRAINT orders_external_id_uq
        UNIQUE (tenant_id, user_id, integration_id, external_order_id)
);

COMMENT ON COLUMN public.orders.cached_payload IS
    'Optional external-source cache only; cache_expires_at is required when populated.';

CREATE INDEX orders_tenant_user_updated_idx
    ON public.orders (tenant_id, user_id, updated_at DESC, id DESC);

CREATE INDEX orders_active_cache_expiry_idx
    ON public.orders (tenant_id, user_id, cache_expires_at)
    WHERE cached_payload IS NOT NULL;

CREATE TABLE public.shipments (
    id bigint GENERATED ALWAYS AS IDENTITY,
    tenant_id uuid NOT NULL,
    user_id bigint NOT NULL,
    order_id bigint NOT NULL,
    external_shipment_id text,
    carrier text,
    tracking_number text,
    shipment_status text,
    cached_payload jsonb,
    source_updated_at timestamptz,
    fetched_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cache_expires_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT shipments_pk PRIMARY KEY (id),
    CONSTRAINT shipments_tenant_user_id_uq UNIQUE (tenant_id, user_id, id),
    CONSTRAINT shipments_order_fk
        FOREIGN KEY (tenant_id, user_id, order_id)
        REFERENCES public.orders (tenant_id, user_id, id),
    CONSTRAINT shipments_external_id_not_blank_ck
        CHECK (external_shipment_id IS NULL OR btrim(external_shipment_id) <> ''),
    CONSTRAINT shipments_carrier_not_blank_ck CHECK (carrier IS NULL OR btrim(carrier) <> ''),
    CONSTRAINT shipments_tracking_not_blank_ck
        CHECK (tracking_number IS NULL OR btrim(tracking_number) <> ''),
    CONSTRAINT shipments_status_not_blank_ck
        CHECK (shipment_status IS NULL OR btrim(shipment_status) <> ''),
    CONSTRAINT shipments_cached_payload_object_ck
        CHECK (cached_payload IS NULL OR jsonb_typeof(cached_payload) = 'object'),
    CONSTRAINT shipments_cache_pair_ck CHECK (
        (cached_payload IS NULL AND cache_expires_at IS NULL)
        OR (cached_payload IS NOT NULL AND cache_expires_at IS NOT NULL)
    ),
    CONSTRAINT shipments_cache_expiry_ck
        CHECK (cache_expires_at IS NULL OR cache_expires_at > fetched_at),
    CONSTRAINT shipments_updated_at_ck CHECK (updated_at >= created_at)
);

COMMENT ON COLUMN public.shipments.cached_payload IS
    'Optional external-source cache only; cache_expires_at is required when populated.';

CREATE UNIQUE INDEX shipments_external_id_uq
    ON public.shipments (tenant_id, user_id, order_id, external_shipment_id)
    WHERE external_shipment_id IS NOT NULL;

CREATE INDEX shipments_order_updated_idx
    ON public.shipments (tenant_id, user_id, order_id, updated_at DESC, id DESC);

CREATE INDEX shipments_tracking_number_idx
    ON public.shipments (tenant_id, user_id, tracking_number)
    WHERE tracking_number IS NOT NULL;

CREATE TABLE public.tracking_events (
    id bigint GENERATED ALWAYS AS IDENTITY,
    tenant_id uuid NOT NULL,
    user_id bigint NOT NULL,
    shipment_id bigint NOT NULL,
    external_event_id text,
    event_status text NOT NULL,
    event_description text,
    event_location text,
    occurred_at timestamptz NOT NULL,
    source_payload jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT tracking_events_pk PRIMARY KEY (id),
    CONSTRAINT tracking_events_shipment_fk
        FOREIGN KEY (tenant_id, user_id, shipment_id)
        REFERENCES public.shipments (tenant_id, user_id, id),
    CONSTRAINT tracking_events_external_id_not_blank_ck
        CHECK (external_event_id IS NULL OR btrim(external_event_id) <> ''),
    CONSTRAINT tracking_events_status_not_blank_ck CHECK (btrim(event_status) <> ''),
    CONSTRAINT tracking_events_description_not_blank_ck
        CHECK (event_description IS NULL OR btrim(event_description) <> ''),
    CONSTRAINT tracking_events_location_not_blank_ck
        CHECK (event_location IS NULL OR btrim(event_location) <> ''),
    CONSTRAINT tracking_events_source_payload_object_ck
        CHECK (source_payload IS NULL OR jsonb_typeof(source_payload) = 'object')
);

CREATE UNIQUE INDEX tracking_events_external_id_uq
    ON public.tracking_events (tenant_id, user_id, shipment_id, external_event_id)
    WHERE external_event_id IS NOT NULL;

CREATE INDEX tracking_events_shipment_occurred_idx
    ON public.tracking_events (tenant_id, user_id, shipment_id, occurred_at DESC, id DESC);

CREATE TABLE public.tool_executions (
    id bigint GENERATED ALWAYS AS IDENTITY,
    tenant_id uuid NOT NULL,
    user_id bigint NOT NULL,
    conversation_id bigint,
    integration_id bigint,
    tool_name text NOT NULL,
    execution_status text NOT NULL,
    request_metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    response_metadata jsonb,
    error_code text,
    started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at timestamptz,
    CONSTRAINT tool_executions_pk PRIMARY KEY (id),
    CONSTRAINT tool_executions_user_fk
        FOREIGN KEY (tenant_id, user_id)
        REFERENCES public.users (tenant_id, id),
    CONSTRAINT tool_executions_conversation_fk
        FOREIGN KEY (tenant_id, user_id, conversation_id)
        REFERENCES public.conversations (tenant_id, user_id, id),
    CONSTRAINT tool_executions_integration_fk
        FOREIGN KEY (tenant_id, user_id, integration_id)
        REFERENCES public.integrations (tenant_id, user_id, id),
    CONSTRAINT tool_executions_name_not_blank_ck CHECK (btrim(tool_name) <> ''),
    CONSTRAINT tool_executions_status_not_blank_ck CHECK (btrim(execution_status) <> ''),
    CONSTRAINT tool_executions_request_metadata_object_ck
        CHECK (jsonb_typeof(request_metadata) = 'object'),
    CONSTRAINT tool_executions_response_metadata_object_ck
        CHECK (response_metadata IS NULL OR jsonb_typeof(response_metadata) = 'object'),
    CONSTRAINT tool_executions_error_code_not_blank_ck
        CHECK (error_code IS NULL OR btrim(error_code) <> ''),
    CONSTRAINT tool_executions_completed_at_ck
        CHECK (completed_at IS NULL OR completed_at >= started_at)
);

COMMENT ON COLUMN public.tool_executions.request_metadata IS
    'Redacted execution metadata only; credentials and raw secrets are prohibited.';
COMMENT ON COLUMN public.tool_executions.response_metadata IS
    'Redacted execution metadata only; credentials and raw secrets are prohibited.';

CREATE INDEX tool_executions_user_started_idx
    ON public.tool_executions (tenant_id, user_id, started_at DESC, id DESC);

CREATE INDEX tool_executions_conversation_started_idx
    ON public.tool_executions (tenant_id, user_id, conversation_id, started_at DESC)
    WHERE conversation_id IS NOT NULL;

CREATE INDEX tool_executions_integration_started_idx
    ON public.tool_executions (tenant_id, user_id, integration_id, started_at DESC)
    WHERE integration_id IS NOT NULL;

CREATE TABLE public.audit_events (
    id bigint GENERATED ALWAYS AS IDENTITY,
    tenant_id uuid NOT NULL,
    actor_user_id bigint,
    event_type text NOT NULL,
    resource_type text,
    resource_id text,
    outcome text NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT audit_events_pk PRIMARY KEY (id),
    CONSTRAINT audit_events_actor_fk
        FOREIGN KEY (tenant_id, actor_user_id)
        REFERENCES public.users (tenant_id, id),
    CONSTRAINT audit_events_type_not_blank_ck CHECK (btrim(event_type) <> ''),
    CONSTRAINT audit_events_resource_type_not_blank_ck
        CHECK (resource_type IS NULL OR btrim(resource_type) <> ''),
    CONSTRAINT audit_events_resource_id_not_blank_ck
        CHECK (resource_id IS NULL OR btrim(resource_id) <> ''),
    CONSTRAINT audit_events_outcome_not_blank_ck CHECK (btrim(outcome) <> ''),
    CONSTRAINT audit_events_metadata_object_ck CHECK (jsonb_typeof(metadata) = 'object')
);

COMMENT ON COLUMN public.audit_events.metadata IS
    'Redacted audit metadata only; credentials and raw secrets are prohibited.';

CREATE INDEX audit_events_tenant_occurred_idx
    ON public.audit_events (tenant_id, occurred_at DESC, id DESC);

CREATE INDEX audit_events_actor_occurred_idx
    ON public.audit_events (tenant_id, actor_user_id, occurred_at DESC)
    WHERE actor_user_id IS NOT NULL;

