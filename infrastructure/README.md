# AWS DEV/QA foundation — review-only, not deployed

Local verification on 2026-10-04: offline definition checks and 10 security contract tests passed.
No CloudFormation/Terraform plan, IAM permission simulation or cloud runtime was verified.

Processes 41–43 continue checkpoint b6c1215449cd87ae8551b1ecc195397185288029.
The repository had no Terraform/CDK/CloudFormation, deployment workflow, provisioned-resource
inventory or approved account/region. Existing dev/qa Spring profiles and containers are reused.
The API Gateway OpenAPI remains a routing inventory, NOT an importable deployment template.

## What is implemented

environments/dev.json and qa.json are tool-neutral, machine-readable **infrastructure requirements
definitions**, not AWS task definitions, CloudFormation templates, Terraform plans or deployable IaC.
They preserve unresolved approvals as null and deployment as BLOCKED. The offline validator rejects
unsafe data exposure, shared environment boundaries, missing TLS/probes, fabricated approvals and
inline secret fields. It validates definitions, not the state of an AWS account. No script calls AWS
or deploys resources. Environment resourceScope strings are proposed logical namespaces, not resource IDs.

Recommended minimal IaC choice: plain CloudFormation after account, region, ingress, budget and
resource choices are approved. There is no established tool to extend; do not add CDK dependencies,
Terraform state or a framework merely to demonstrate deployment. No tool was installed. Actual IaC
generation, cfn-lint/cfn-guard and account-aware validation remain NOT VERIFIED/DECISION REQUIRED.

From repository root:

```sh
node infrastructure/validate-environments.mjs
node --test infrastructure/tests/*.test.mjs
```

## Minimum secure candidate architecture (not an existing AWS deployment)

| Component evaluated | Candidate / decision |
| --- | --- |
| VPC, subnets, routes | Independent DEV and QA VPCs; private compute and isolated data subnet groups spanning availability zones. No data-subnet internet route. Public subnets only if selected ingress/NAT needs them. CIDRs/AZs not invented. |
| Security groups | Database 5432 and Redis configured service port only from environment backend task SG; task ingress only from reviewed private ingress/TLS tier. No public task IP, cross-environment ingress, public DB/cache or direct edge bypass. Egress/DNS controls reviewed separately. |
| ECS/Fargate | Separate backend and Next standalone web services per environment, private awsvpc tasks, non-root images, explicit architecture-compatible image digests. CPU/memory/desired count require measured sizing and cost approval; no EKS control plane needed. |
| ECR | Separate environment/release image promotion policy; immutable digest deployment, scanning and rollback-digest retention. No image pushed. Host-built ARM images are not automatically suitable for x86 tasks. |
| RDS PostgreSQL | Separate DEV/QA instances/databases and environment application roles; private subnet groups, no public access, encryption, TLS hostname/CA verification, backups and restore rehearsal. Engine version/instance/storage/HA not chosen without region availability and cost review. |
| ElastiCache | Separate DEV/QA Redis-compatible caches, private access, TLS and authentication, environment-specific namespace as defense in depth. Namespace alone is NOT isolation. Existing Lua-based rate limits must be exercised against chosen engine/version/TLS. |
| Secrets Manager | Environment-specific database, JWT signing and Redis secrets injected at task launch; values never in JSON, build args or images. This is infrastructure-secret delivery, NOT a new customer credential store. Rotation requires task replacement and compatibility review. |
| KMS | Encryption required for data, secrets and logs. Decide service-managed versus customer-managed keys and rotation/cost policy before generating resource policies. Never invent key ARNs. |
| CloudWatch | Separate structured application/access/flow log destinations, encryption, finite approved retention, restricted readership, minimal health/availability alarms. Exclude request/response bodies, Authorization, token responses and credentials. Exported metrics/alarms are NOT implemented. |
| API Gateway | Retain explicit existing routes and Spring bearer validation. Choose REST/WAF or separately reviewed ingress; private integration and backend TLS are unresolved. No public Actuator proxy or invented JWT/JWKS authorizer. See API-GATEWAY-DESIGN.md. |
| WAF | Required review for any internet-facing API: applicable managed protections and coarse rate limits; preserve backend Redis controls. No existing protection disabled. Ingress choice must prove WAF applicability and no bypass before publication. |
| Route 53 / ACM | Approved domains and certificates only; no hosted zone/domain created. AWS HTTPS endpoint may defer custom DNS after ingress approval. |
| CloudFront | Optional reviewed web edge, not an automatic new layer. Next standalone currently needs a Node runtime; an S3 static upload is not equivalent. Do not cache authenticated API/chat data. |
| IAM | Separate deployer, execution and application responsibilities. Execution role only approved image pulls/log writes/infrastructure-secret reads and applicable key decrypt. Application has no AWS calls today: no Bedrock, secret-store or administrative grants. No policies generated or permission claims made. |

Sources reviewed: [ECS network security](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/security-network.html),
[ECS secret injection](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/specifying-sensitive-data.html),
[RDS PostgreSQL TLS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html),
[ElastiCache TLS](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/in-transit-encryption.html).

## Runtime configuration contract

Keep existing profiles; do not change local/prod behavior. Inject SPRING_PROFILES_ACTIVE=dev or qa,
SPRING_APPLICATION_NAME scoped to environment, SERVER_PORT=8080, SHUTDOWN_TIMEOUT=30s,
LOG_STRUCTURED_FORMAT=logstash, and AUTH_RATE_LIMIT_NAMESPACE from the environment definition.
Provide environment-owned DATABASE_URL, REDIS_HOST/PORT and exact APP_CORS_ALLOWED_ORIGINS explicitly;
never rely on current localhost/CORS defaults for deployment.

DATABASE_URL must use sslmode=verify-full plus a trusted RDS CA at an approved path.
CA delivery is NOT configured in the existing image. Set SPRING_DATA_REDIS_SSL_ENABLED=true through
Spring environment binding for deployed Redis; inspect/verify chosen Redis AUTH/ACL compatibility.
TLS connection/runtime verification remains BLOCKED. No local plaintext connection is relabeled secure.

Use ECS secrets mappings for DATABASE_USERNAME, DATABASE_PASSWORD, AUTH_ACCESS_TOKEN_SIGNING_KEY and
REDIS_PASSWORD. The app still requires its existing Argon2id/JWT/hash/rotation/session/ownership checks.
Do not configure AWS access keys in containers. IAM execution-role secret injection does not give
the application a competing credential-handling mechanism.

NEXT_PUBLIC_API_BASE_URL is build-time PUBLIC configuration. Build separate reviewed web images for
approved DEV/QA HTTPS API origins; runtime environment changes cannot rewrite the browser bundle.
Frontend/extension source and local host permissions are unchanged.

Probe liveness for container life and readiness (DB + Redis) for traffic admission. Do not assume curl
exists in runtime images: select a verified load-balancer probe or install/approve a dedicated probe.
Stop timeout candidate 60s leaves headroom over current 30s graceful shutdown; cloud drain unverified.

## Deployment blockers and safety gates

Before ANY AWS write, require verified identity/account/region, explicit permission review, quota and
resource-name conflict discovery, available budget/credits, TLS certificates/CA delivery, supported
private ingress/WAF, image sizing/architecture/scans, and a reviewed plan/change set. No wildcard
administration, no assumed existing role. Generate least-privilege policies only for resolved resources;
document unavoidable service-scoping exceptions separately (e.g. ECR authorization-token resource scope).

Backend HTTP port 8080 has no TLS configured. A TLS endpoint alone does not encrypt the last hop.
Certificate handling/re-encryption needs a separately approved deployment configuration; never silently
use plaintext internal forwarding to claim end-to-end TLS. Existing synchronous chat timeout versus
Gateway timeout remains an explicit blocker. No budgets or business/application logic changed here.

Flyway remains the schema authority, validate-on-migrate/clean-disabled and Hibernate ddl-auto=none.
V1–V7 immutable. For first deployment, back up/restore-test the isolated database and gate one controlled
migrating task before service scaling; Flyway locking is not a substitute for a deployment strategy.
Later releases need expand/contract compatibility checks. Roll back image/task revision only when
compatible with the resulting schema; never run destructive down-migrations to undo deployment.

QA promotes the reviewed backend digest from DEV, but rebuilds the web image for its own API origin.
QA has synthetic-only data and independent secrets, VPC, DB, cache, services, logs and origins.
No production/customer secret copying or DEV DB/cache endpoints. Do not restore DEV customer data to QA.

## Cost gate

No account credits, budget balance, regional pricing or exact estimate verified. Before provisioning,
price: two environments' Fargate hours/vCPU/memory, private ingress/load balancer, RDS compute/storage/
backups, cache capacity, NAT hours/data versus endpoint hours/data, public IPv4 where applicable,
Gateway requests, WAF rules/requests, optional CDN/DNS, ECR storage/scanning, log ingestion/retention,
Secrets Manager and KMS requests/keys. Isolation has a cost; do not quietly share stores to save credits.
Do not provision just to test definitions. DEV/QA stop/scale schedules, HA and retention choices require
budget and availability approval. No numerical cost/savings claims or new paid resources.
