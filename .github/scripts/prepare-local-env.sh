#!/usr/bin/env bash
# Disposable GitHub-hosted runner infrastructure only; never customer/AWS secrets.
set -euo pipefail
test "${GITHUB_ACTIONS:-}" = true
test -n "${GITHUB_ENV:-}"
database_password=$(openssl rand -hex 32)
signing_key=$(openssl rand -base64 48 | tr -d '\n')
echo "::add-mask::$database_password"
echo "::add-mask::$signing_key"
{
  echo 'POSTGRES_DB=order_delivery_ci'
  echo 'POSTGRES_USER=order_delivery_ci'
  echo "POSTGRES_PASSWORD=$database_password"
  echo "DATABASE_PASSWORD=$database_password"
  echo 'DATABASE_USERNAME=order_delivery_ci'
  echo 'DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/order_delivery_ci'
  echo "AUTH_ACCESS_TOKEN_SIGNING_KEY=$signing_key"
  echo 'SPRING_PROFILES_ACTIVE=local'
  echo 'REDIS_HOST=127.0.0.1'
  echo 'PYTHONDONTWRITEBYTECODE=1'
} >> "$GITHUB_ENV"
