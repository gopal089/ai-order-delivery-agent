import type {
  ExtensionMessage,
  ExtensionResponse,
} from '../shared/messages'
import {
  DEFAULT_SETTINGS,
  normalizeBackendBaseUrl,
  validateBackendBaseUrl,
  type ExtensionSettings,
} from '../shared/settings'

const SETTINGS_KEY = 'settings'

async function readSettings(): Promise<ExtensionSettings> {
  const stored = await chrome.storage.sync.get(SETTINGS_KEY)
  return {
    ...DEFAULT_SETTINGS,
    ...(stored[SETTINGS_KEY] as Partial<ExtensionSettings> | undefined),
  }
}

async function saveSettings(
  settings: ExtensionSettings,
): Promise<ExtensionSettings> {
  const error = validateBackendBaseUrl(settings.backendBaseUrl)

  if (error) {
    throw new Error(error)
  }

  const normalized: ExtensionSettings = {
    backendBaseUrl: normalizeBackendBaseUrl(settings.backendBaseUrl),
  }

  await chrome.storage.sync.set({ [SETTINGS_KEY]: normalized })
  return normalized
}

chrome.runtime.onInstalled.addListener(async () => {
  const stored = await chrome.storage.sync.get(SETTINGS_KEY)

  if (!stored[SETTINGS_KEY]) {
    await chrome.storage.sync.set({ [SETTINGS_KEY]: DEFAULT_SETTINGS })
  }
})

chrome.runtime.onMessage.addListener(
  (
    message: ExtensionMessage,
    _sender: chrome.runtime.MessageSender,
    sendResponse: (response: ExtensionResponse) => void,
  ) => {
    const handleMessage = async (): Promise<ExtensionResponse> => {
      if (message.type === 'GET_SETTINGS') {
        return { ok: true, settings: await readSettings() }
      }

      if (message.type === 'SAVE_SETTINGS') {
        return { ok: true, settings: await saveSettings(message.settings) }
      }

      return { ok: false, error: 'Unsupported extension message.' }
    }

    handleMessage()
      .then(sendResponse)
      .catch((error: unknown) => {
        const message =
          error instanceof Error ? error.message : 'Unable to update settings.'
        sendResponse({ ok: false, error: message })
      })

    return true
  },
)
