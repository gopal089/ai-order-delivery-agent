import { type FormEvent, useEffect, useState } from 'react'
import { Brand } from '../components/Brand'
import { sendExtensionMessage } from '../shared/messages'
import {
  normalizeBackendBaseUrl,
  validateBackendBaseUrl,
} from '../shared/settings'

type SaveStatus =
  | { type: 'idle'; message: '' }
  | { type: 'success' | 'error'; message: string }

export function Options() {
  const [backendBaseUrl, setBackendBaseUrl] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [status, setStatus] = useState<SaveStatus>({
    type: 'idle',
    message: '',
  })

  useEffect(() => {
    sendExtensionMessage({ type: 'GET_SETTINGS' })
      .then((response) => {
        if (!response.ok) {
          setStatus({ type: 'error', message: response.error })
          return
        }

        setBackendBaseUrl(response.settings.backendBaseUrl)
      })
      .catch(() => {
        setStatus({
          type: 'error',
          message: 'Unable to load extension settings.',
        })
      })
      .finally(() => setIsLoading(false))
  }, [])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const validationError = validateBackendBaseUrl(backendBaseUrl)

    if (validationError) {
      setStatus({ type: 'error', message: validationError })
      return
    }

    setIsSaving(true)
    setStatus({ type: 'idle', message: '' })

    try {
      const response = await sendExtensionMessage({
        type: 'SAVE_SETTINGS',
        settings: {
          backendBaseUrl: normalizeBackendBaseUrl(backendBaseUrl),
        },
      })

      if (!response.ok) {
        setStatus({ type: 'error', message: response.error })
        return
      }

      setBackendBaseUrl(response.settings.backendBaseUrl)
      setStatus({ type: 'success', message: 'Backend address saved.' })
    } catch {
      setStatus({ type: 'error', message: 'Unable to save extension settings.' })
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <main className="options">
      <header className="options__header">
        <Brand />
        <span>Extension settings</span>
      </header>

      <div className="options__layout">
        <section className="options__intro" aria-labelledby="settings-title">
          <p className="eyebrow">Connection</p>
          <h1 id="settings-title">Configure your approved backend.</h1>
          <p>
            The extension connects only to your application backend. Customer
            API keys and infrastructure credentials must never be entered here.
          </p>
        </section>

        <form className="settings-form" onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="backend-base-url">Backend base URL</label>
            <input
              id="backend-base-url"
              name="backendBaseUrl"
              type="url"
              inputMode="url"
              placeholder="https://your-backend.example"
              value={backendBaseUrl}
              onChange={(event) => {
                setBackendBaseUrl(event.target.value)
                setStatus({ type: 'idle', message: '' })
              }}
              disabled={isLoading || isSaving}
              aria-describedby="backend-help save-status"
              autoComplete="url"
            />
            <p id="backend-help">
              HTTPS is required except for localhost development. Do not include
              credentials, query parameters, or fragments.
            </p>
          </div>

          <div className="security-note">
            <strong>What belongs here</strong>
            <p>
              Only the base address for the backend created by this project.
              External order-provider secrets remain server-side.
            </p>
          </div>

          <div className="form-actions">
            <button
              className="button"
              type="submit"
              disabled={isLoading || isSaving}
            >
              {isSaving ? 'Saving…' : 'Save connection'}
            </button>
            <p
              id="save-status"
              className={`save-status save-status--${status.type}`}
              role="status"
            >
              {status.message}
            </p>
          </div>
        </form>
      </div>
    </main>
  )
}
