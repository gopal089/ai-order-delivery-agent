"use client";

import { useEffect, useState, type FormEvent } from "react";
import { AppShell } from "@/components/app-shell";
import { ControlledAnswer } from "@/components/controlled-answer";
import { apiClient, type Integration } from "@/lib/api-client";
import { chatApi, chatError } from "@/lib/chat-api";
import { canSend, newConversationState } from "@/lib/chat-state";
import Link from "next/link";
import styles from "./chat.module.css";

function ChatPanel() {
  const [integrations, setIntegrations] = useState<Integration[] | null>(null);
  const [integrationId, setIntegrationId] = useState("");
  const [conversation, setConversation] = useState(newConversationState);
  const { conversationId, message, turns } = conversation;
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    apiClient.integrations().then((rows) => { if (active) setIntegrations(rows); })
      .catch((caught: unknown) => { if (active) setError(chatError(caught)); });
    return () => { active = false; };
  }, []);
  async function send(event: FormEvent) {
    event.preventDefault();
    if (!canSend(integrationId, message, pending) || !integrations?.some((item) => item.enabled && item.id === Number(integrationId))) return;
    setPending(true); setError("");
    const question = message.trim();
    try {
      const id = conversationId ?? (await chatApi.create()).id;
      setConversation((previous) => ({ ...previous, conversationId: id, notice: "" }));
      const answer = await chatApi.send(id, Number(integrationId), question);
      setConversation((previous) => ({ ...previous, turns: [...previous.turns, { question, answer }], message: "" }));
    } catch (caught) { setError(chatError(caught)); }
    finally { setPending(false); }
  }
  return <section className={styles.panel}>
    <p>Only our backend is contacted. Provider fields are reported with source timestamps; model prose is withheld. AI availability depends on backend configuration.</p>
    {integrations === null && !error && <p role="status">Loading integrations…</p>}
    {integrations !== null && !integrations.some((item) => item.enabled) && <p>No enabled integrations are available. <Link href="/integrations">Create integration metadata</Link> before sending.</p>}
    <p>Saved conversation browsing is not available yet: the backend has no conversation-list endpoint.</p>
    {conversation.notice && <p role="status">{conversation.notice}</p>}
    <form onSubmit={send}>
      <label htmlFor="chat-integration">Your integration</label>
      <select id="chat-integration" value={integrationId} disabled={pending} onChange={(event) => setIntegrationId(event.target.value)}>
        <option value="">Choose an integration</option>
        {integrations?.filter((item) => item.enabled).map((item) => <option key={item.id} value={item.id}>{item.displayName}</option>)}
      </select>
      <label htmlFor="chat-message">Ask about an order or shipment</label>
      <textarea id="chat-message" maxLength={4000} required value={message} disabled={pending} onChange={(event) => setConversation((previous) => ({ ...previous, message: event.target.value }))} />
      <button disabled={!canSend(integrationId, message, pending)}>{pending ? "Waiting for backend…" : "Send"}</button>
      <button type="button" disabled={pending} onClick={() => { if (pending) return; setConversation(newConversationState()); setError(""); }}>New conversation</button>
    </form>
    {error && <p role="alert">{error}</p>}
    <div aria-live="polite">
      {turns.map(({ question, answer }) => <article key={answer.assistantMessageId} className={styles.turn}>
        <h2>You</h2><p>{question}</p>
        <h2>Backend response</h2>
        <ControlledAnswer answer={answer} />
      </article>)}
    </div>
  </section>;
}
export default function ChatPage() {
  return <AppShell eyebrow="Controlled assistance" title="Order chat" description="Ask using your authenticated backend connection.">
    <ChatPanel />
  </AppShell>;
}
