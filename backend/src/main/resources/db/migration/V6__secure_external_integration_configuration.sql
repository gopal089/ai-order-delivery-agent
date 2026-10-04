ALTER TABLE public.integrations
    ADD COLUMN base_url text,
    ADD COLUMN credential_reference text,
    ADD COLUMN credential_type text,
    ADD CONSTRAINT integrations_base_url_not_blank_ck
        CHECK (base_url IS NULL OR btrim(base_url) <> ''),
    ADD CONSTRAINT integrations_credential_reference_not_blank_ck
        CHECK (credential_reference IS NULL OR btrim(credential_reference) <> ''),
    ADD CONSTRAINT integrations_credential_type_not_blank_ck
        CHECK (credential_type IS NULL OR btrim(credential_type) <> ''),
    ADD CONSTRAINT integrations_credential_pair_ck CHECK (
        (credential_reference IS NULL AND credential_type IS NULL)
        OR (credential_reference IS NOT NULL AND credential_type IS NOT NULL)
    );

COMMENT ON COLUMN public.integrations.base_url IS
    'Normalized customer provider base URL. Treat as untrusted input and revalidate before outbound use.';

COMMENT ON COLUMN public.integrations.credential_reference IS
    'Opaque reference to an external credential store. Raw credential material is prohibited.';

COMMENT ON COLUMN public.integrations.credential_type IS
    'Non-secret credential configuration type only; never credential material.';
