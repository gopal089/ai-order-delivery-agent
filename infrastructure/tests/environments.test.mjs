import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadDefinitions, validateDefinitions } from '../validate-environments.mjs';

test('review-only DEV/QA definitions validate without AWS', () => {
  assert.equal(validateDefinitions(loadDefinitions()).definitionValidation, 'PASS');
  assert.equal(validateDefinitions(loadDefinitions()).deployment, 'BLOCKED');
});
for (const field of ['assignPublicIp','databasePublic','redisPublic','allowCrossEnvironmentTraffic']) {
  test('reject unsafe networking: ' + field, () => {
    const d=loadDefinitions(); d[0].network[field]=true;
    assert.throws(() => validateDefinitions(d));
  });
}
test('reject environment/profile/Redis identity collision', () => {
  for (const field of ['backendProfile','redisNamespace']) {
    const d=loadDefinitions(); d[1].runtime[field]=d[0].runtime[field];
    assert.throws(() => validateDefinitions(d));
  }
});
test('reject shared data and secret stores', () => {
  for (const field of ['database','redis','secrets','logs','services']) {
    const d=loadDefinitions(); d[1].isolation[field]='shared';
    assert.throws(() => validateDefinitions(d));
  }
});
test('reject production/customer data and inline secret fields', () => {
  for (const field of ['productionAccess','customerCredentials']) {
    const d=loadDefinitions();d[0].isolation[field]=true;
    assert.throws(() => validateDefinitions(d));
  }
  const d=loadDefinitions();d[0].secretInjection.password='synthetic-only';
  assert.throws(() => validateDefinitions(d));
});
test('reject disabled TLS and missing probes', () => {
  for (const field of ['redisTls','publicHttpsRequired']) {
    const d=loadDefinitions();d[0].runtime[field]=false;
    assert.throws(() => validateDefinitions(d));
  }
  const d=loadDefinitions();d[0].runtime.healthPaths=[];
  assert.throws(() => validateDefinitions(d));
});
test('reject fabricated AWS/model approval or deployment readiness', () => {
  const d=loadDefinitions();d[0].approved.region='synthetic-region';
  assert.throws(() => validateDefinitions(d));
  const b=loadDefinitions();b[0].bedrock.implemented=true;
  assert.throws(() => validateDefinitions(b));
  const c=loadDefinitions();c[0].iac.deployable=true;
  assert.throws(() => validateDefinitions(c));
});
