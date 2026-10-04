import { apiClient, ApiError, type Integration } from "./api-client";

export interface CreateIntegration { providerKey: string; displayName: string; baseUrl: string; enabled: boolean }
export type IntegrationFields = Partial<Record<"providerKey" | "displayName" | "baseUrl", string>>;
export function validateIntegration(input: CreateIntegration): IntegrationFields {
  const errors: IntegrationFields = {};
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$/.test(input.providerKey.trim())) errors.providerKey = "Use 1–100 letters, numbers, dots, underscores or hyphens; start with a letter or number.";
  if (!input.displayName.trim() || input.displayName.trim().length > 200) errors.displayName = "Enter a display name of at most 200 characters.";
  try {
    const url = new URL(input.baseUrl.trim());
    if (input.baseUrl.trim().length > 2048 || !["https:", "http:"].includes(url.protocol) || url.username || url.password || url.search || url.hash) throw new Error();
  } catch { errors.baseUrl = "Enter a complete HTTP(S) base URL without credentials, query parameters or fragments. The backend enforces permitted hosts and HTTPS policy."; }
  return errors;
}
export function integrationError(error: unknown): { message: string; fields: IntegrationFields } {
  if (!(error instanceof ApiError)) return { message: "The backend request could not be completed.", fields: {} };
  const messages: Record<number, string> = { 400: "Integration settings are invalid. Check the fields and allowed base URL.", 401: "Your session expired. Please sign in again.", 403: "Access denied.", 409: "An integration with these settings already exists.", 429: "Too many requests. Wait before retrying.", 503: "The backend service is unavailable. Try again later." };
  const fields: IntegrationFields = {};
  // Reflect only known fields with fixed safe messages, never exception text or rejected values.
  if (Object.hasOwn(error.fieldErrors, "providerKey")) fields.providerKey = "Check the provider key format and length (maximum 100).";
  if (Object.hasOwn(error.fieldErrors, "displayName")) fields.displayName = "A display name is required (maximum 200 characters).";
  if (Object.hasOwn(error.fieldErrors, "baseUrl")) fields.baseUrl = "Check the base URL (maximum 2048 characters).";
  return { message: (messages[error.status] ?? "The backend request failed. Try again later.") + (error.requestId ? ` Request ID: ${error.requestId}` : ""), fields };
}
export const integrationApi = {
  create: (input: CreateIntegration) => apiClient.post<Integration, CreateIntegration>("/api/v1/integrations", {
    providerKey: input.providerKey.trim(), displayName: input.displayName.trim(), baseUrl: input.baseUrl.trim(), enabled: input.enabled,
  }, true),
};
