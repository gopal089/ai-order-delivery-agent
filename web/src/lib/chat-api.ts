import { apiClient, ApiError } from "./api-client";

export interface Conversation { id: number; createdAt: string }
export interface ControlledFact {
  toolSequence: number;
  tool: string;
  field: "ORDER_STATUS" | "SHIPMENT_STATUS" | "TRACKING_STATUS" | "TRACKING_LOCATION" | "PACKAGE_LOCATION";
  value: string | null;
  sourceTimestamp: string | null;
  valueTrust: "EXTERNAL_UNTRUSTED";
}
export interface ConversationTurn {
  userMessageId: number; assistantMessageId: number; text: string;
  trust: "MODEL_GENERATED_UNVERIFIED";
  externalDataRetrieved: boolean;
  supportStatus: "MODEL_GENERATED_UNVERIFIED" | "EXTERNALLY_SUPPORTED" | "PARTIALLY_SUPPORTED";
  tools: { tool: string; externalDataRetrieved: boolean; errorCode: string | null }[];
  facts: ControlledFact[];
}
export function chatError(error: unknown): string {
  if (!(error instanceof ApiError)) return "The request could not be completed.";
  const messages: Record<number, string> = {
    401: "Your session expired. Please sign in again.",
    403: "You do not have access to this conversation or integration.",
    429: "Too many requests. Please wait before trying again.",
    503: "AI service unavailable or not configured. No production model is available yet.",
  };
  return (messages[error.status] ?? "The backend request failed. Please try again later.") +
    (error.requestId ? ` Request ID: ${error.requestId}` : "");
}
export const chatApi = {
  create: () => apiClient.post<Conversation, Record<string, never>>("/api/v1/conversations", {}, true),
  send: (id: number, integrationId: number, message: string) => {
    if (!Number.isSafeInteger(id) || id <= 0 || !Number.isSafeInteger(integrationId) || integrationId <= 0)
      throw new Error("Invalid conversation selection");
    return apiClient.post<ConversationTurn, { integrationId: number; message: string }>(
      `/api/v1/conversations/${id}/messages`, { integrationId, message }, true);
  },
};
