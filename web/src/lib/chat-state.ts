import type { ConversationTurn } from "./chat-api";
export interface ChatState { conversationId: number | null; message: string; turns: { question: string; answer: ConversationTurn }[]; notice: string }
export function newConversationState(): ChatState {
  return { conversationId: null, message: "", turns: [], notice: "New conversation ready. Your draft is empty; previous saved conversations are unchanged. A conversation is created when you first send." };
}
export function canSend(integrationId: string, message: string, pending: boolean): boolean {
  return !pending && Number.isSafeInteger(Number(integrationId)) && Number(integrationId) > 0 && !!message.trim() && message.trim().length <= 4000;
}
