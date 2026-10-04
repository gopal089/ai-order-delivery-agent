import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import assert from 'node:assert/strict';

// Offline contract validation only. No AWS SDK/CLI calls or resource creation.
export function validateDefinitions(definitions) {
  assert.equal(definitions.length, 2, 'DEV and QA definitions required');
  assert.deepEqual(definitions.map(d => d.environment).sort(), ['dev', 'qa']);
  for (const d of definitions) {
    assert.equal(d.schemaVersion, 1);
    assert.equal(d.kind, 'review-only-environment-definition');
    assert.equal(d.deploymentStatus, 'BLOCKED');
    assert.equal(d.iac.deployable, false);
    assert.equal(d.resourceScope, 'ai-order-delivery-agent-' + d.environment);
    assert.deepEqual(Object.keys(d.approved).sort(), ['accountId','apiOrigin','backendImageDigest','budget','ingress','region','webImageDigest','webOrigin'].sort());
    assert.ok(Object.values(d.approved).every(v => v === null), 'No approval or deployment may be inferred');
    for (const k of ['separateVpc','privateCompute','privateDataSubnets']) assert.equal(d.network[k], true, k);
    for (const k of ['assignPublicIp','databasePublic','redisPublic','allowCrossEnvironmentTraffic']) assert.equal(d.network[k], false, k);
    assert.equal(d.network.securityGroupSources, 'security-group-references-only');
    assert.equal(d.runtime.backendProfile, d.environment);
    assert.equal(d.runtime.redisNamespace, 'auth:ratelimit:' + d.environment);
    assert.equal(d.runtime.databaseTls, 'verify-full-with-approved-CA');
    assert.equal(d.runtime.redisTls, true);
    assert.equal(d.runtime.publicHttpsRequired, true);
    assert.deepEqual(d.runtime.healthPaths, ['/actuator/health/liveness','/actuator/health/readiness']);
    assert.equal(d.runtime.logFormat, 'logstash');
    assert.ok(d.runtime.stopTimeoutSeconds > Number.parseInt(d.runtime.shutdownTimeout));
    assert.equal(d.runtime.migrationPolicy, 'existing-Flyway-V1-V7-validate-on-migrate-no-clean-no-DDL');
    assert.equal(d.runtime.imagePolicy, 'immutable-reviewed-digest-per-environment');
    assert.equal(d.isolation.database, 'separate-instance');
    assert.equal(d.isolation.redis, 'separate-cache');
    assert.equal(d.isolation.services, 'separate-backend-and-web-services');
    assert.equal(d.isolation.secrets, 'separate-environment-secrets');
    assert.equal(d.isolation.logs, 'separate-environment-log-groups');
    assert.equal(d.isolation.testData, 'synthetic-only');
    assert.equal(d.isolation.productionAccess, false);
    assert.equal(d.isolation.customerCredentials, false);
    assert.equal(d.secretInjection.valuesIncluded, false);
    assert.equal(d.secretInjection.mechanism, 'ECS-secret-injection-from-Secrets-Manager');
    assert.deepEqual(d.secretInjection.variables, ['DATABASE_USERNAME','DATABASE_PASSWORD','AUTH_ACCESS_TOKEN_SIGNING_KEY','REDIS_PASSWORD']);
    assert.equal(d.iam.adminPermissions, false);
    assert.equal(d.bedrock.status, 'BLOCKED');
    assert.equal(d.bedrock.modelId, null);
    assert.equal(d.bedrock.implemented, false);
    // Definition contains only known fields. No credentials or opaque extra payloads.
    assert.deepEqual(Object.keys(d).sort(), ['schemaVersion','kind','deploymentStatus','approved','network','runtime','isolation','secretInjection','iam','bedrock','iac','environment','resourceScope'].sort());
    const forbidden = /^(?:password|token|accessKey|secretAccessKey|credentialValue|privateKey)$/i;
    const inspect = value => { if (value && typeof value === 'object') for (const [k,v] of Object.entries(value)) { assert.ok(!forbidden.test(k), 'Secret-valued field prohibited'); inspect(v); } };
    inspect(d);
  }
  assert.notEqual(definitions[0].resourceScope, definitions[1].resourceScope);
  assert.notEqual(definitions[0].runtime.redisNamespace, definitions[1].runtime.redisNamespace);
  return { definitionValidation: 'PASS', deployment: 'BLOCKED', environments: ['dev','qa'], awsResourcesCreated: 0 };
}
export function loadDefinitions() {
  return ['dev','qa'].map(env => JSON.parse(readFileSync(new URL('./environments/' + env + '.json', import.meta.url), 'utf8')));
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { console.log(JSON.stringify(validateDefinitions(loadDefinitions()), null, 2)); }
  catch { console.error('FAIL: invalid environment definition; values withheld'); process.exitCode = 1; }
}
