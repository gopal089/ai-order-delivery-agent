import { useEffect, useState } from 'react'
import { Brand } from '../components/Brand'
import { sendExtensionMessage } from '../shared/messages'
import type { ExtensionSettings } from '../shared/settings'
import { validateBackendBaseUrl } from '../shared/settings'
import { Chat } from './Chat'

type PopupState =
  | { status: 'loading' }
  | { status: 'ready'; settings: ExtensionSettings }
  | { status: 'error'; message: string }

export function Popup() {
  const [state, setState] = useState<PopupState>({ status: 'loading' })

  useEffect(() => {
    sendExtensionMessage({ type: 'GET_SETTINGS' })
      .then((response) => {
        if (response.ok) {
          setState({ status: 'ready', settings: response.settings })
          return
        }

        setState({ status: 'error', message: response.error })
      })
      .catch(() => {
        setState({
          status: 'error',
          message: 'The extension background service is unavailable.',
        })
      })
  }, [])

  const isConfigured =
    state.status === 'ready' && !validateBackendBaseUrl(state.settings.backendBaseUrl)

  return (
    <main className="popup">
      <header className="popup__header">
        <Brand />
        <span className="popup__version">v0.1</span>
      </header>

      <section className="popup__intro" aria-labelledby="popup-title">
        <p className="eyebrow">Secure delivery assistance</p>
        <h1 id="popup-title">What would you like to know?</h1>
        <p>
          Ask about an order after your organization’s backend connection is
          configured.
        </p>
      </section>

      <section className="popup__status" aria-live="polite">
        {state.status === 'loading' && (
          <p className="status status--loading">Checking extension setup…</p>
        )}

        {state.status === 'error' && (
          <div className="status status--error">
            <strong>Setup check failed</strong>
            <p>{state.message}</p>
          </div>
        )}

        {state.status === 'ready' && (
          <div className={`status ${isConfigured ? 'status--ready' : ''}`}>
            <strong>
              {isConfigured ? 'Backend configured' : 'Backend setup required'}
            </strong>
            <p>
              {isConfigured
                ? 'Sign in to send controlled questions to your local backend. AI may be unavailable.'
                : 'Add the approved backend address before using the agent.'}
            </p>
          </div>
        )}
      </section>

      {isConfigured && state.status === 'ready' && <Chat key={state.settings.backendBaseUrl} baseUrl={state.settings.backendBaseUrl} />}
      <button
        className="button popup__button"
        type="button"
        onClick={() => chrome.runtime.openOptionsPage()}
      >
        Open extension settings
        <span aria-hidden="true">↗</span>
      </button>

      <footer className="popup__footer">
        <span aria-hidden="true">◆</span>
        Provider credentials never enter this extension. Session tokens stay in popup memory only.
      </footer>
    </main>
  )
}
