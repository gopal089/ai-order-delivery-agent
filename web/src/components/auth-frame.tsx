import Link from "next/link";

import styles from "./auth-frame.module.css";

interface AuthFrameProps {
  eyebrow: string;
  title: string;
  description: string;
  children: React.ReactNode;
}

export function AuthFrame({
  eyebrow,
  title,
  description,
  children,
}: AuthFrameProps) {
  return (
    <main className={styles.page}>
      <section className={styles.intro}>
        <Link className={styles.brand} href="/" aria-label="Order Agent home">
          <span aria-hidden="true">OA</span>
          Order Agent
        </Link>
        <div>
          <p className={styles.eyebrow}>{eyebrow}</p>
          <h1>{title}</h1>
          <p className={styles.description}>{description}</p>
        </div>
        <p className={styles.securityNote}>
          Authentication and tenant identity are verified by the backend.
        </p>
      </section>
      <section className={styles.card}>{children}</section>
    </main>
  );
}
