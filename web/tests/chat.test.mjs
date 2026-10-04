import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import ts from 'typescript';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
// Compile the actual client modules into a disposable directory using the existing TS compiler.
const temporary = mkdtempSync(new URL('../node_modules/.chat-tests-', import.meta.url).pathname);
after(() => rmSync(temporary, { recursive: true, force: true }));
for (const [source, name] of [['src/lib/auth-session.ts','auth-session'],['src/lib/api-client.ts','api-client'],['src/lib/chat-api.ts','chat-api'],['src/lib/chat-state.ts','chat-state'],['src/lib/integration-api.ts','integration-api'],['src/components/controlled-answer.tsx','controlled-answer']]) {
  const input = readFileSync(new URL(`../${source}`, import.meta.url), 'utf8').replace(/from "\.\/([^".]+)"/g, 'from "./$1.mjs"');
  writeFileSync(`${temporary}/${name}.mjs`, ts.transpileModule(input, { compilerOptions: { module: ts.ModuleKind.ESNext, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2023 } }).outputText);
}
process.env.NEXT_PUBLIC_API_BASE_URL = 'http://127.0.0.1:18088';
const { chatApi, chatError } = await import(pathToFileURL(`${temporary}/chat-api.mjs`));
const { ApiError, apiClient } = await import(pathToFileURL(`${temporary}/api-client.mjs`));
const { setAuthSession, clearAuthSession, getAuthSession } = await import(pathToFileURL(`${temporary}/auth-session.mjs`));
const { ControlledAnswer } = await import(pathToFileURL(`${temporary}/controlled-answer.mjs`));
const { validateIntegration, integrationError, integrationApi } = await import(pathToFileURL(`${temporary}/integration-api.mjs`));
const { newConversationState, canSend } = await import(pathToFileURL(`${temporary}/chat-state.mjs`));
test('chat requires an in-memory authenticated session', async () => {
  clearAuthSession(); await assert.rejects(chatApi.create(), { status: 401 });
});
test('chat uses exact backend bodies and never client identity fields', async () => {
  setAuthSession({ email: 'fixture@example.test', accessToken: 'synthetic-test-access', refreshToken: 'synthetic-test-refresh', refreshTokenExpiresAt: new Date(Date.now()+60000).toISOString(), accessTokenExpiresAt: Date.now()+60000 });
  const original = globalThis.fetch; const calls = [];
  globalThis.fetch = async (url, options) => { calls.push({ url, body: JSON.parse(options.body) }); assert.equal(options.headers.get('Authorization'), 'Bearer synthetic-test-access'); assert.equal(options.credentials, 'omit'); assert.equal(options.redirect, 'error'); return Response.json({ id: 7 }); };
  try { await chatApi.create(); await chatApi.send(7, 9, 'Where is my order?'); }
  finally { globalThis.fetch = original; clearAuthSession(); }
  assert.deepEqual(calls, [{ url:'http://127.0.0.1:18088/api/v1/conversations', body:{} },{ url:'http://127.0.0.1:18088/api/v1/conversations/7/messages', body:{integrationId:9,message:'Where is my order?'} }]);
  assert.throws(() => chatApi.send(-1, 9, 'question'));
});
for (const status of [401,403,429,503,500]) test(`safe ${status} message retains request ID`, () => {
  const text = chatError(new ApiError(status, 'fixture', 'sensitive-detail', {}, 'fixture-request'));
  assert.ok(text.includes('fixture-request')); assert.ok(!text.includes('sensitive-detail'));
});
test('no retrieval: model Chennai claim never appears as supported evidence', () => {
  const html = renderToStaticMarkup(createElement(ControlledAnswer,{answer:{text:'I checked the external system and your package is in Chennai.', externalDataRetrieved:false, supportStatus:'MODEL_GENERATED_UNVERIFIED', facts:[]}}));
  assert.ok(!html.includes('Chennai')); assert.ok(!html.includes('Provider-supported fields')); assert.ok(html.includes('No supported provider fields'));
});
test('facts are escaped text with exact source timestamp, not model prose', () => {
  const html = renderToStaticMarkup(createElement(ControlledAnswer,{answer:{text:'invented-model-claim',supportStatus:'EXTERNALLY_SUPPORTED',facts:[{field:'PACKAGE_LOCATION', value:'<img src=x onerror=alert(1)>',sourceTimestamp:'2026-10-02T12:00:00Z'}]}}));
  assert.ok(!html.includes('invented-model-claim')); assert.ok(!html.includes('<img')); assert.ok(html.includes('&lt;img')); assert.ok(html.includes('2026-10-02T12:00:00Z'));
});
test('pending refresh cannot restore a logged-out web session', async () => {
  setAuthSession({email:'fixture@example.test',accessToken:'synthetic-access',refreshToken:'synthetic-refresh',refreshTokenExpiresAt:new Date(Date.now()+60000).toISOString(),accessTokenExpiresAt:Date.now()+60000});
  const original=globalThis.fetch; let release; let started;
  const refreshing=new Promise(resolve => { started=resolve; });
  globalThis.fetch=async (url) => {
    if (url.endsWith('/refresh')) { started(); return await new Promise(resolve => { release=resolve; }); }
    return new Response(null,{status:401,headers:{'X-Request-ID':'fixture-request'}});
  };
  const request=chatApi.create(); const failed=assert.rejects(request,{status:401,requestId:'fixture-request'});
  try {
    await refreshing; clearAuthSession();
    release(Response.json({accessToken:'synthetic-new',refreshToken:'synthetic-new-refresh',refreshTokenExpiresAt:new Date(Date.now()+60000).toISOString(),accessTokenExpiresInSeconds:900}));
    await failed; assert.equal(getAuthSession(),null);
  } finally { globalThis.fetch=original; clearAuthSession(); }
});
test('integration validation covers required fields and forbids URL credentials', () => {
  assert.equal(Object.keys(validateIntegration({providerKey:'',displayName:' ',baseUrl:'',enabled:true})).length,3);
  assert.deepEqual(validateIntegration({providerKey:' documented-provider ',displayName:' My integration ',baseUrl:'https://example.com/api',enabled:false}),{});
  for (const baseUrl of ['https://user:synthetic@example.com','https://example.com?key=synthetic','https://example.com#token','javascript:alert(1)']) assert.ok(validateIntegration({providerKey:'test',displayName:'Test',baseUrl,enabled:true}).baseUrl);
  assert.ok(validateIntegration({providerKey:'invalid spaces',displayName:'Test',baseUrl:'https://example.com',enabled:true}).providerKey);
});
test('integration create success and owned list refresh use exact metadata-only contract', async () => {
  setAuthSession({email:'fixture@example.test',accessToken:'synthetic-access',refreshToken:'synthetic-refresh',refreshTokenExpiresAt:new Date(Date.now()+60000).toISOString(),accessTokenExpiresAt:Date.now()+60000});
  const original=globalThis.fetch; const calls=[]; const created={id:8,providerKey:'test',displayName:'Test',baseUrl:'https://example.com',enabled:true,credentialConfigured:false};
  globalThis.fetch=async (url,init) => {calls.push({url,method:init.method,body:init.body?JSON.parse(init.body):undefined}); assert.equal(init.headers.get('Authorization'),'Bearer synthetic-access'); return Response.json(init.method==='POST'?created:[created]);};
  try {
    assert.deepEqual(await integrationApi.create({providerKey:' test ',displayName:' Test ',baseUrl:' https://example.com ',enabled:true,tenantId:'forged',userId:99,credentialReference:'forged'}),created);
    assert.deepEqual(await apiClient.integrations(),[created]);
    assert.deepEqual(calls,[{url:'http://127.0.0.1:18088/api/v1/integrations',method:'POST',body:{providerKey:'test',displayName:'Test',baseUrl:'https://example.com',enabled:true}},{url:'http://127.0.0.1:18088/api/v1/integrations',method:'GET',body:undefined}]);
  } finally {globalThis.fetch=original;clearAuthSession();}
});
test('empty integration backend result stays empty, not fake records', async () => {
  setAuthSession({email:'fixture@example.test',accessToken:'synthetic',refreshToken:'synthetic',refreshTokenExpiresAt:new Date(Date.now()+60000).toISOString(),accessTokenExpiresAt:Date.now()+60000});
  const original=globalThis.fetch;globalThis.fetch=async () => Response.json([]);
  try {assert.deepEqual(await apiClient.integrations(),[]);} finally {globalThis.fetch=original;clearAuthSession();}
});
for (const status of [400,401,403,409,429,503,500]) test(`integration ${status} failure is safe and retains request ID`, () => {
  const failure=integrationError(new ApiError(status,'fixture','sensitive exception',{providerKey:'sensitive rejected value',credentialReference:'sensitive secret'},'fixture-request'));
  assert.ok(failure.message.includes('fixture-request'));assert.ok(!JSON.stringify(failure).includes('sensitive'));assert.deepEqual(Object.keys(failure.fields),['providerKey']);
});
test('integration backend failure is not reported as successful creation', async () => {
  setAuthSession({email:'fixture@example.test',accessToken:'synthetic',refreshToken:'synthetic',refreshTokenExpiresAt:new Date(Date.now()+60000).toISOString(),accessTokenExpiresAt:Date.now()+60000});
  const original=globalThis.fetch;globalThis.fetch=async () => Response.json({message:'sensitive exception'},{status:500,headers:{'X-Request-ID':'fixture-failure'}});
  try {await assert.rejects(integrationApi.create({providerKey:'test',displayName:'Test',baseUrl:'https://example.com',enabled:true}),{status:500,requestId:'fixture-failure'});} finally {globalThis.fetch=original;clearAuthSession();}
});
test('New conversation clears draft/id/messages and supplies visible feedback without touching previous data', () => {
  const previous={conversationId:7,message:'old draft',turns:[{question:'old question',answer:{text:'persisted controlled answer'}}]};
  const next=newConversationState();assert.equal(next.conversationId,null);assert.equal(next.message,'');assert.deepEqual(next.turns,[]);assert.ok(next.notice.includes('New conversation ready'));assert.equal(previous.turns.length,1);assert.equal(previous.conversationId,7);
});
test('Send gating requires a valid selected ID, nonempty question, and no pending request', () => {
  assert.equal(canSend('','question',false),false);assert.equal(canSend('8',' ',false),false);assert.equal(canSend('8','question',true),false);assert.equal(canSend('-1','question',false),false);assert.equal(canSend('forged','question',false),false);assert.equal(canSend('8','question',false),true);
});
