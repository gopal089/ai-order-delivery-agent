import type { ExtensionSettings } from './settings'

export type ExtensionMessage =
  | { type: 'GET_SETTINGS' }
  | { type: 'SAVE_SETTINGS'; settings: ExtensionSettings }

export type ExtensionResponse =
  | { ok: true; settings: ExtensionSettings }
  | { ok: false; error: string }

export function sendExtensionMessage(
  message: ExtensionMessage,
): Promise<ExtensionResponse> {
  return chrome.runtime.sendMessage(message) as Promise<ExtensionResponse>
}
