import Link from "next/link";
import styles from "./page.module.css";

const capabilities = [
  {
    number: "01",
    title: "Ask naturally",
    description:
      "Look up orders, shipment status, tracking history, and the latest provider-reported location in plain language.",
  },
  {
    number: "02",
    title: "Verify at the source",
    description:
      "Approved tools retrieve current information from your configured order and tracking providers before the agent answers.",
  },
  {
    number: "03",
    title: "Keep control",
    description:
      "Authorization, tenant isolation, and credential access remain in the backend—never in the model or browser.",
  },
];

const safeguards = [
  "Credentials remain server-side",
  "Tenant-scoped authorization",
  "Source timestamps preserved",
];

export default function Home() {
  return (
    <div className={styles.page}>
      <a className={styles.skipLink} href="#main-content">
        Skip to content
      </a>

      <header className={styles.header}>
        <Link
          className={styles.brand}
          href="/"
          aria-label="AI Order & Delivery Agent home"
        >
          <span className={styles.brandMark} aria-hidden="true">
            OA
          </span>
          <span>Order Agent</span>
        </Link>
        <nav className={styles.nav} aria-label="Primary navigation">
          <a href="#capabilities">Capabilities</a>
          <a href="#how-it-works">How it works</a>
          <Link href="/login">Login</Link>
          <Link className={styles.navCta} href="/register">
            Get started
          </Link>
        </nav>
      </header>

      <main id="main-content">
        <section className={styles.hero} id="top">
          <div className={styles.heroCopy}>
            <p className={styles.eyebrow}>
              <span aria-hidden="true" /> Secure order intelligence
            </p>
            <h1>
              Ask where an order is.
              <span> Get an answer grounded in your systems.</span>
            </h1>
            <p className={styles.heroLead}>
              The AI Order &amp; Delivery Agent connects your operations team to
              trusted data—without exposing customer credentials to the AI.
            </p>
            <div className={styles.heroActions}>
              <a className={styles.primaryCta} href="#how-it-works">
                See how it works
                <span aria-hidden="true">↘</span>
              </a>
              <p>Built for multi-tenant operations</p>
            </div>
          </div>

          <div className={styles.agentPreview} aria-label="Example agent interaction">
            <div className={styles.previewHeader}>
              <div>
                <span className={styles.statusDot} aria-hidden="true" />
                Agent ready
              </div>
              <span>Example</span>
            </div>
            <div className={styles.conversation}>
              <div className={styles.userMessage}>
                <p className={styles.messageLabel}>You</p>
                <p>Where is order [order ID] right now?</p>
              </div>
              <div className={styles.agentMessage}>
                <div className={styles.toolStatus}>
                  <span aria-hidden="true">✓</span>
                  Checking your connected tracking provider
                </div>
                <p>
                  I’ll report only the latest status and location returned by
                  your provider, including its source timestamp.
                </p>
                <div className={styles.responseMeta}>
                  <span>Authorized tool call</span>
                  <span>Credentials protected</span>
                </div>
              </div>
            </div>
            <div className={styles.promptBar} aria-hidden="true">
              <span>Ask about an order or shipment…</span>
              <span>↵</span>
            </div>
          </div>
        </section>

        <ul className={styles.safeguards} aria-label="Platform safeguards">
          {safeguards.map((safeguard) => (
            <li key={safeguard}>
              <span aria-hidden="true">◆</span>
              {safeguard}
            </li>
          ))}
        </ul>

        <section className={styles.capabilities} id="capabilities">
          <div className={styles.sectionIntro}>
            <p className={styles.sectionLabel}>Purpose-built for delivery operations</p>
            <h2>Fast answers. Controlled access. No invented tracking data.</h2>
          </div>
          <ol className={styles.capabilityList}>
            {capabilities.map((capability) => (
              <li key={capability.number}>
                <span className={styles.capabilityNumber}>{capability.number}</span>
                <div>
                  <h3>{capability.title}</h3>
                  <p>{capability.description}</p>
                </div>
              </li>
            ))}
          </ol>
        </section>

        <section className={styles.howItWorks} id="how-it-works">
          <div className={styles.flowCopy}>
            <p className={styles.sectionLabel}>One controlled path to the source</p>
            <h2>The model reasons. Your backend decides and acts.</h2>
            <p>
              The platform separates natural-language understanding from
              credentials, authorization, and external API access. Each layer
              has one job.
            </p>
          </div>
          <ol className={styles.flow}>
            <li>
              <span>01</span>
              <strong>Question</strong>
              <p>A user asks about an order.</p>
            </li>
            <li>
              <span>02</span>
              <strong>Control</strong>
              <p>The backend validates identity and tool input.</p>
            </li>
            <li>
              <span>03</span>
              <strong>Source</strong>
              <p>The approved provider returns current data.</p>
            </li>
            <li>
              <span>04</span>
              <strong>Answer</strong>
              <p>The agent summarizes only verified fields.</p>
            </li>
          </ol>
        </section>

        <section className={styles.earlyAccess} id="early-access">
          <p className={styles.sectionLabel}>Early access</p>
          <h2>Bring clarity to every delivery conversation.</h2>
          <p>
            Create an account, sign in securely, and verify your tenant-scoped
            integration connection.
          </p>
          <Link className={styles.earlyAccessStatus} href="/register">
            Create your account →
          </Link>
        </section>
      </main>

      <footer className={styles.footer}>
        <div className={styles.brand}>
          <span className={styles.brandMark} aria-hidden="true">
            OA
          </span>
          <span>Order Agent</span>
        </div>
        <p>AI Order &amp; Delivery Agent</p>
        <p>Designed for factual, secure assistance.</p>
      </footer>
    </div>
  );
}
