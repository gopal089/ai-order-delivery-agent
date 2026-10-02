# Local development infrastructure

This project uses Docker Compose to run PostgreSQL and Redis locally. On macOS, either OrbStack or Docker Desktop can provide the Docker engine and the `docker compose` command. This configuration is for local development only; it does not define or connect to production or AWS infrastructure.

## Prerequisites

- OrbStack or Docker Desktop is installed and running.
- `docker compose version` succeeds in a terminal.
- Ports `5432` and `6379` are available on localhost.

## Create the local environment file

From the project root:

```sh
cp .env.example .env
```

Replace `POSTGRES_PASSWORD` in `.env` with a local-development-only password. Do not reuse a personal, production, or shared password. The root `.gitignore` excludes `.env` and other local environment files while keeping `.env.example` tracked.

Set `AUTH_ACCESS_TOKEN_SIGNING_KEY` to a Base64-encoded random value of at least 32 bytes. Generate it locally and never commit or paste the real value into documentation or logs.

## Start the services

```sh
docker compose up -d
```

Docker Compose creates the local network and the `postgres_data` and `redis_data` named volumes automatically.

## Check service status

```sh
docker compose ps
```

Both services should report `healthy` after initialization.

## View logs

All services:

```sh
docker compose logs
```

Follow logs continuously:

```sh
docker compose logs -f postgres redis
```

## PostgreSQL connection information

From the macOS host:

- Host: `localhost`
- Port: `5432`
- Database, username, and password: values in the local `.env`

From another container on this Compose network:

- Host: `postgres`
- Port: `5432`

Verify a connection through the container:

```sh
docker compose exec postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT 1;"'
```

## Redis connection information

From the macOS host:

- Host: `localhost`
- Port: `6379`

From another container on this Compose network:

- Host: `redis`
- Port: `6379`

Verify a connection:

```sh
docker compose exec redis redis-cli ping
```

Redis uses append-only persistence with an `everysec` fsync policy, backed by the `redis_data` named volume. It is intentionally unauthenticated for loopback-only local development and is not suitable for production use.

## User registration

The backend exposes one registration endpoint:

```text
POST /api/v1/auth/register
```

Request body:

```json
{
  "email": "developer@example.com",
  "password": "LocalExample!234"
}
```

The password must be 12–128 characters and include uppercase, lowercase, number, and special characters.

Example local request:

```sh
curl --request POST http://localhost:8080/api/v1/auth/register \
  --header 'Content-Type: application/json' \
  --data '{"email":"developer@example.com","password":"LocalExample!234"}'
```

Successful response (`201 Created`):

```json
{
  "id": 1,
  "email": "developer@example.com",
  "createdAt": "2026-10-02T06:00:00Z"
}
```

Passwords and password hashes are never returned. Common errors are:

- `400 VALIDATION_ERROR`: email or password is missing or invalid, or the password does not satisfy the policy.
- `400 INVALID_REQUEST`: the request body is missing or is not valid JSON.
- `409 EMAIL_ALREADY_REGISTERED`: the email address is already registered. Email comparison is case-insensitive.

## Login and session endpoints

Login creates a 15-minute signed access token and a rotating 30-day refresh token:

```text
POST /api/v1/auth/login
```

```json
{
  "email": "developer@example.com",
  "password": "LocalExample!234"
}
```

Rotate the current refresh token at `POST /api/v1/auth/refresh` with a JSON body containing `refreshToken`. Reusing an older rotated token revokes the entire refresh-token session. End a session at `POST /api/v1/auth/logout` with the same request shape; successful and idempotent logout returns `204 No Content`.

Raw refresh tokens are returned only at issuance and are never stored. PostgreSQL stores their SHA-256 hashes. Authentication failures deliberately use one generic `401 AUTHENTICATION_FAILED` response for unknown email, incorrect password, inactive users, and invalid refresh-token state.

See `docs/AUTHENTICATION.md` for token format, expiration, rotation, reuse-detection, and session semantics.

## Stop the services

Stop and remove the containers while retaining their data volumes:

```sh
docker compose down
```

## Completely reset local data

The following command permanently deletes the local PostgreSQL and Redis volumes for this project:

```sh
docker compose down --volumes
```

Start the services again with `docker compose up -d` to create empty volumes.
