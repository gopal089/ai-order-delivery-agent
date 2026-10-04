import { useState, type FormEvent } from 'react'
import { ExtensionSession, safeError, type Integration, type ChatTurn } from '../api/session'

export function Chat({ baseUrl }: { baseUrl: string }) {
  const [session] = useState(() => new ExtensionSession(baseUrl))
  const [signedIn, setSignedIn] = useState(false)
  const [email, setEmail] = useState(''); const [password, setPassword] = useState('')
  const [integrations, setIntegrations] = useState<Integration[]>([])
  const [integration, setIntegration] = useState(''); const [conversation, setConversation] = useState<number | null>(null)
  const [question, setQuestion] = useState(''); const [answer, setAnswer] = useState<ChatTurn | null>(null)
  const [pending, setPending] = useState(false); const [error, setError] = useState('')
  async function run(action: () => Promise<void>) {
    if (pending) return
    setPending(true); setError('')
    try { await action() } catch (caught) {
      setError(safeError(caught)); setAnswer(null)
      if (!session.authenticated) { setSignedIn(false); setConversation(null); setIntegrations([]); setIntegration(''); setQuestion('') }
    }
    finally { setPending(false) }
  }
  function login(event: FormEvent) {
    event.preventDefault()
    const submitted = password; setPassword('')
    void run(async () => {
      await session.login(email, submitted); setSignedIn(true)
      setIntegrations(await session.request<Integration[]>('/api/v1/integrations'))
    })
  }
  function send(event: FormEvent) {
    event.preventDefault()
    if (!integration || !question.trim()) return
    void run(async () => {
      const id = conversation ?? (await session.request<{ id: number }>('/api/v1/conversations', { method: 'POST', body: '{}' })).id
      if (!Number.isSafeInteger(id) || id <= 0) throw new Error('Invalid conversation')
      setConversation(id)
      setAnswer(await session.request<ChatTurn>(`/api/v1/conversations/${id}/messages`, { method: 'POST', body: JSON.stringify({ integrationId: Number(integration), message: question.trim() }) }))
    })
  }
  return <section>
    {!signedIn ? <form onSubmit={login}>
      <label>Email<input type="email" required value={email} onChange={(event) => setEmail(event.target.value)} disabled={pending} autoComplete="username" /></label>
      <label>Password<input type="password" required value={password} onChange={(event) => setPassword(event.target.value)} disabled={pending} autoComplete="current-password" /></label>
      <button disabled={pending}>Sign in</button>
    </form> : <>
      <p>Popup-memory session. Closing the popup requires signing in again.</p>
      <form onSubmit={send}>
        <label>Integration<select value={integration} onChange={(event) => setIntegration(event.target.value)} disabled={pending}><option value="">Choose</option>{integrations.filter((item) => item.enabled).map((item) => <option value={item.id} key={item.id}>{item.displayName}</option>)}</select></label>
        {integrations.length === 0 && <p>No configured integrations.</p>}
        <label>Question<textarea required maxLength={4000} value={question} onChange={(event) => setQuestion(event.target.value)} disabled={pending} /></label>
        <button disabled={pending || !integration || !question.trim()}>Send</button>
      </form>
      <button disabled={pending} onClick={() => { void run(async () => { try { await session.logout() } finally { setSignedIn(false); setAnswer(null); setConversation(null); setIntegrations([]) } }) }}>Sign out</button>
    </>}
    {pending && <p role="status">Waiting for backend…</p>}
    {error && <p role="alert">{error}</p>}
    {answer && <div aria-live="polite">
      <p>{answer.supportStatus === 'EXTERNALLY_SUPPORTED' ? 'Provider-supported fields; not independent verification' : 'No verified current factual answer'}</p>
      {answer.facts.length === 0 ? <p>No supported provider fields were returned.</p> : <dl>{answer.facts.map((fact, index) => <div key={index}><dt>{fact.field}</dt><dd>{fact.value ?? 'Unavailable'}</dd><dd>Source timestamp: {fact.sourceTimestamp ?? 'Unavailable'}</dd></div>)}</dl>}
      <p>Model claims withheld. No ETA or freshness inferred.</p>
    </div>}
  </section>
}
