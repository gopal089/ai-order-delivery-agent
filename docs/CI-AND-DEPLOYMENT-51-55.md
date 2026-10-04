# Processes 51–55 — verification and deployment boundary

Recorded 2026-10-04. Starting branch `main`, HEAD and local `origin/main` both
`7df95692914a51e4a7fe8df5b5ccf9bf22df35ab`; working tree initially clean.
This batch is uncommitted. No commit, push, AWS authentication, image publishing,
AWS resource write or deployment was performed. Documentation is not cloud evidence.

## 51 — CI: implemented locally; remote execution NOT VERIFIED

Previously `.github` contained only `.gitkeep`. `workflows/ci.yml` now verifies
pushes/PRs targeting main and manual dispatches, with bounded job timeouts and
per-ref concurrency. Only `contents: read`; checkout credentials are not persisted.
All seven official action usages are pinned to resolved 40-character commit SHAs.
The action tags/SHAs and scanner release checksum were checked against GitHub's
official release/commit APIs. No `pull_request_target`, OIDC permission, AWS login,
cloud credentials, publish or deployment job was added.

Jobs:

- Contracts/security: CI safety tests, all evaluation Python tests, existing DEV/QA
  definition validator/tests, Gitleaks 8.30.1 over full Git history. The downloaded
  Linux binary is SHA-256 checked; scanner output is redacted, with no report artifact.
- Backend: Temurin Java 21; the existing Compose PostgreSQL/Redis on a disposable
  GitHub-hosted runner. `prepare-local-env.sh` generates random database password
  and signing key, masks them before exporting them through `GITHUB_ENV`, and
  refuses to run outside Actions. No committed `.env`, AWS/customer secret, or
  fixed password. `report.py --run --scope full` invokes the existing Gradle full
  suite; failed/skipped/absent evidence fails the gate. Independent baseline and
  bootJar follow. Only sanitized `full.json`/`baseline.json` retained for seven days;
  no raw JUnit XML, diagnostics, app logs, environment, JAR or image artifact upload.
- Clients: Node 24, npm ci from existing lockfiles, lint, web typecheck, both builds,
  existing tests and high-severity production-dependency audit. Development
  dependencies are NOT certified by that audit; known full-audit findings remain.
- Docker: requires every earlier job; builds both existing Dockerfiles with
  `ci-<commit>` tags/revision labels and explicitly LOCAL public web API origin.
  Smoke checks on disposable Compose infrastructure; no registry push. These are
  verification images, not deployable approved release artifacts.

Runner cleanup uses `docker compose down --volumes` ONLY on ephemeral hosted
runner infrastructure. Do not execute that cleanup against your existing local
database volumes. Local smoke removes only the exact disposable container IDs it
created, preserves existing Compose infrastructure/data, and retains built images.

Current GitHub read-only metadata: main `protected=false`, repository rulesets `[]`,
environments count zero. Branch-protection detail endpoint returned HTTP 404;
that endpoint alone is not proof of protection absence. Required status checks,
review ownership and protected release environments need owner decisions. No
remote settings were changed. No commit/push means the new workflow has not run
on GitHub; local component results do not establish a hosted-runner pass.

## 52 — Docker: existing design preserved; local builds VERIFIED

No Dockerfile/ignore/Compose change. Backend uses Temurin 21 JDK build/JRE runtime,
UID/GID 10001, port 8080, runtime-only private configuration, graceful SIGTERM.
Its build context allowlists main source/Gradle files, not tests or `.env`.
Web uses Node 24 multi-stage/Next standalone, UID/GID 1001, port 3000. `.dockerignore`
excludes env/key material, local dependencies/generated build output and tests.
`NEXT_PUBLIC_API_BASE_URL` is PUBLIC build-time configuration; runtime changes do
not rewrite the browser bundle. Local HTTP requires explicit `ALLOW_LOCAL_API=true`.
Neither image embeds customer/AWS credentials. Existing base-image version tags
are mutable, not digest-pinned; dependency/build inputs are not a hermetic supply
chain. Release base digests, image vulnerability scans, provenance/signing, registry
immutability/retention and target CPU architecture remain NOT VERIFIED/DECISION REQUIRED.

Actual commands from repository root (both exit 0):

```sh
docker build --pull --label org.opencontainers.image.revision=7df95692914a51e4a7fe8df5b5ccf9bf22df35ab -t ai-order-backend:p51-local backend
docker build --pull --label org.opencontainers.image.revision=7df95692914a51e4a7fe8df5b5ccf9bf22df35ab --build-arg NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8080 --build-arg ALLOW_LOCAL_API=true -t ai-order-web:p51-local web
```

Actual Docker inspection: both Linux/arm64, backend user `10001:10001`, web user
`1001:1001`. Local image IDs (not ECR verification):

- Backend: `sha256:4743b577e46de482ce9f2485a5db58cbfe4ce9efb069c61a776732931710c078`.
- Web: `sha256:9642c836f9d8bb985fc0f6cf5bca7ba2f4d2aa1281a5e5f5b90de7f0dda2776c`.

`python3 .github/scripts/container_smoke.py --backend ai-order-backend:p51-local
--web ai-order-web:p51-local` ran with private ignored local infrastructure values
in process environment, never printed. PASS: missing config fails closed with
nonzero exit; configured prod-profile backend readiness/liveness 200 UP, protected
integration request without token 401, Flyway validates seven migrations, UID and
graceful shutdown; web `/`, `/login`, `/register`, `/dashboard`, `/integrations`,
`/chat` HTML 200 and UID. HTML checks are NOT authenticated browser interactions.
No Docker HEALTHCHECK is defined: probes are existing HTTP actuator routes and
external smoke requests; no unverified runtime curl assumption.

Local negative build with HTTP origin and no ALLOW_LOCAL_API flag failed at the
existing origin validator as expected. Both image configs/history and exported
application files were scanned against configured local secret markers, env/key
filenames, and packaged test-provider classes: zero findings (backend one app JAR;
web 1,352 app files). This is bounded verification, not exhaustive DLP or a CVE scan.
Native amd64 CI builds and any registry release/promotion remain NOT VERIFIED.

## AWS pre-flight — actual result

Executed read-only `aws --version`, `aws sts get-caller-identity --output json
--no-cli-pager`, `aws configure list`; metadata lookup disabled with
`AWS_EC2_METADATA_DISABLED=true`. CLI 2.37.4, Python 3.14.7, Darwin arm64.
STS exit 253: unable to locate credentials. Configure-list exit 0: profile/region
not set. No secret configuration values printed. AWS account/principal/region
BLOCKED; intended account/approved region, IAM permissions, resources, billing,
budget and credits NOT VERIFIED. No resource discovery was attempted without
authentication. No `aws login`, credential provisioning, ECR login/push or AWS write.

## 53 — DEV: BLOCKED; 54 — QA: BLOCKED

Existing `infrastructure/environments/dev.json` and `qa.json` validate offline.
They explicitly remain review-only, approved values null, deployable=false.
Runtime Spring profiles and isolation intentions are source configuration, not
deployed databases/caches/services/logs/secrets. There is no deployable IaC,
authenticated approved account/region/IAM, verified infrastructure, managed
secrets/KMS configuration, approved ingress/TLS/CA, resource cost/budget, reviewed
image release or tested cloud rollback. DEV prerequisites therefore fail; QA
cannot be promoted from DEV. No ECS/task/service/API health, cloud DB/Redis,
TLS, logs, cloud authentication or representative cloud request was verified.
Local tests are not relabeled DEV/QA cloud verification. No customer API/model calls.

## 55 — production approval: DECISION REQUIRED; deployment prohibited

No repository production deployment definition/pipeline or GitHub environment
approval mechanism found. Spring's prod profile is not a production release gate.
Do not invent approvers, roles, approved budgets, resource IDs or production URLs.
Before designing any deployment pipeline, the owner must decide and verify:

- Correct account/region, least-privilege execution/task/deploy IAM and any OIDC trust.
- Approved deployable IaC, ingress topology/TLS (including backend last hop), WAF,
  private network/egress policy and current gateway/request timeout compatibility.
- Independent DEV/QA/PROD data, caches, log groups and managed secret injection;
  production credentials must never be copied to nonproduction.
- Release owner, enforced approvals/required checks, QA acceptance/security review,
  unresolved full dependency audit, actual evaluation limitations and release digests.
- Migration/runtime privilege separation, backups/restore drills, monitoring,
  alarms/log retention, resource sizing and account budget/cost approval.
- Schema-compatible image/task rollback and an actual isolated rollback exercise.

This is a checklist of missing decisions, not an implemented approval/rollback system.

## Database, secrets, rollback and cost boundaries

Flyway runs automatically on application startup using the configured datasource
role. Validate-on-migrate true, baseline-on-migrate false, clean disabled;
Hibernate ddl-auto none. Direct local read-only SQL: versions 1–7 success=true;
current role superuser/create-db/create-role=true. Thus local runtime can migrate
but is excessive privilege for deployment; dedicated migration/runtime roles and
controlled migration rollout require approval. No separate migration job or
tested expand-contract/database rollback exists. V1–V7 bytes unchanged.

Existing managed-secret strategy is an intended ECS Secrets Manager injection
contract only; no actual Secrets Manager/KMS secrets/keys/roles verified. CI uses
throwaway test secrets, not an implementation of customer credential storage.
No real credential-store/provider/model adapter was introduced. Configured secret
marker scans found no values in local test/build/smoke logs or safe JSON reports;
source snapshot and full history Gitleaks scans had zero findings.

Versioned local image tags and SIGTERM are NOT cloud rollback proof. Previous
reviewed ECR digest/task revision, ECS rollback, database compatibility, backup
restoration and application rollback are NOT VERIFIED. No account prices/usage/
credits/budget retrieved; no numerical bill/estimate/savings claims. Zero AWS
resources were created, not a statement that the account has zero cost. Local
builds consume local resources; future GitHub runner/artifact billing needs owner review.

## Local test record

All commands below ran; private local DB environment/JAVA_HOME configured without
printing values. No existing application/frontend/extension/dependency/migration
file changed.

| Command / check | Actual result |
| --- | --- |
| backend: `./gradlew test --rerun-tasks --console=plain` | 306 tests, 30 suites; zero failures/errors/skips |
| backend: `./gradlew bootJar --console=plain` | PASS |
| `PYTHONDONTWRITEBYTECODE=1 python3 evaluation/report.py --run --scope full` | PASS, 306/306; 23 automated PASS, 3 MANUAL_REVIEW, 1 live-model BLOCKED |
| `PYTHONDONTWRITEBYTECODE=1 python3 evaluation/report.py --run --scope baseline` | PASS, 12/12; not full-category evidence |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s evaluation -p 'test_*.py' -v` | 27 PASS |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s .github/tests -v` | 9 PASS |
| `node infrastructure/validate-environments.mjs`; `node --test infrastructure/tests/*.test.mjs` | Definitions PASS/deployment BLOCKED; 10 tests PASS |
| actionlint 1.7.12: `actionlint -shellcheck= .github/workflows/ci.yml`; `bash -n .github/scripts/prepare-local-env.sh` | PASS; shellcheck unavailable, not claimed |
| web: `npm ci`; `npm run lint`; `npm run typecheck`; `npm run build`; `node --test tests/*.test.mjs` | PASS, 23 tests, zero skips |
| extension: `npm ci`; `npm run lint`; `npm run build`; `node --test tests/*.test.mjs` | PASS, 10 tests, zero skips |
| both clients: `npm audit --omit=dev --audit-level=high` | PASS, zero findings |
| web: `npm audit --json` | exit 1, five high-severity development-dependency findings; unresolved, not a pass |
| Both Docker builds, smoke, negative origin build and bounded image/secret scans | PASS as detailed above |

Registration 7/7, authentication 12/12, authorization 16/16, tenant isolation
12/12. Full report retained before baseline replaces raw JUnit XML. Reports record
workingTreeChanged=true; not an immutable committed release attestation.
Initial client npm ci attempts failed EPERM writing the default user npm cache;
retries using `npm_config_cache=/private/tmp/ai-order-p51-npm-cache-web` and
`...-extension` passed. No chown, dependency upgrade or security bypass. npm also
warns existing ESLint 9.39.5 is deprecated. Local client build public origin was
`NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8080`, not a production origin.
