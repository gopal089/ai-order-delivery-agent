ALTER TABLE public.users
    ADD COLUMN password_hash text;

ALTER TABLE public.users
    ADD CONSTRAINT users_password_hash_argon2_ck
    CHECK (
        password_hash IS NULL
        OR password_hash ~ '^\$argon2(id|i|d)\$v=[0-9]+\$'
    );

COMMENT ON COLUMN public.users.password_hash IS
    'Argon2 encoded password only. Plaintext passwords must never be stored.';

CREATE UNIQUE INDEX users_email_global_uq
    ON public.users (lower(btrim(email)));
