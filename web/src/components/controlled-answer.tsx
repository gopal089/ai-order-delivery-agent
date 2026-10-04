import type { ConversationTurn } from "../lib/chat-api";

/** Render controlled facts as escaped text, never raw model prose or provider HTML. */
export function ControlledAnswer({ answer }: { answer: ConversationTurn }) {
  return <>
    <p>{answer.supportStatus === "EXTERNALLY_SUPPORTED" ? "Provider-supported fields; not independent verification" : "No verified current factual answer"}</p>
    {answer.facts.length === 0 ? <p>No supported provider fields were returned.</p> : <dl>{answer.facts.map((fact, index) => <div key={index}>
      <dt>{fact.field.replaceAll("_", " ")}</dt><dd>{fact.value ?? "Unavailable"}</dd>
      <dd>Provider source timestamp: {fact.sourceTimestamp ?? "Unavailable"}</dd>
    </div>)}</dl>}
    <p>Model-generated claims are not displayed. No delivery estimate or freshness is inferred.</p>
  </>;
}
