import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const requirements = JSON.parse(readFileSync(new URL('../observability/requirements.json', import.meta.url)));
test('observability requirements cannot imply deployment or invented resources', () => {
  assert.equal(requirements.kind, 'review-only-observability-requirements');
  assert.equal(requirements.deployable, false);
  assert.equal(requirements.productionDeployment, 'BLOCKED');
  for (const key of ['account', 'region', 'logGroup', 'retentionDays', 'kmsKey', 'executionRole']) assert.equal(requirements.cloudwatch[key], null);
  assert.equal(requirements.cloudwatch.awslogsConfigured, false);
  assert.equal(requirements.cloudwatch.alarmsVerified, false);
});
test('no exporter or sensitive capture is silently enabled', () => {
  const t = requirements.telemetry;
  assert.equal(t.default, 'NOOP'); assert.equal(t.exportEnabled, false);
  assert.equal(t.collectorEndpoint, null); assert.equal(t.approvedExportDestination, null);
  assert.equal(t.samplingPolicy, null);
  assert.deepEqual(t.spanAttributeAllowlist, ['http.request.method', 'http.route', 'http.response.status_code']);
  for (const key of ['customerIdentityLabels','requestIdLabel','bodyCapture','headerCapture','sqlStatementCapture','exceptionMessageCapture','promptResponseCapture']) assert.equal(t[key], false);
});
test('Datadog integration and alert delivery remain unverified', () => {
  for (const key of ['organization','site','agentEndpoint']) assert.equal(requirements.datadog[key], null);
  for (const key of ['apiKeyIncluded','dashboardsVerified','monitorsVerified']) assert.equal(requirements.datadog[key], false);
  for (const key of ['alertThreshold','evaluationWindow','notificationDestination','alertOwner']) assert.equal(requirements.securityMonitoring[key], null);
  assert.equal(requirements.securityMonitoring.deliveryTested, false);
  assert.equal(requirements.securityMonitoring.infrastructureEventsVerified, false);
  assert.equal(requirements.securityMonitoring.semanticPromptInjectionDetectionImplemented, false);
});
