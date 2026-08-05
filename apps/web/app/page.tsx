import Link from "next/link";

import { authenticatedSession } from "@/lib/backend";

export default async function Home() {
  const session = await authenticatedSession();
  return (
    <main className="landing">
      <nav className="nav">
        <div className="brand">
          <span className="brandMark">M</span>
          MedRAG
        </div>
        <div className="secureBadge">Private by default</div>
      </nav>
      <section className="hero">
        <div>
          <p className="eyebrow">SECURE CLINICAL INTELLIGENCE</p>
          <h1>Find the facts buried inside patient records.</h1>
          <p className="heroCopy">
            Tenant-isolated document retrieval and evidence-grounded summaries,
            designed for private clinics and clinical teams.
          </p>
          <div className="actions">
            {session ? (
              <Link className="primary" href="/dashboard">
                Open dashboard
              </Link>
            ) : (
              <Link className="primary" href="/api/auth/login">
                Sign in securely
              </Link>
            )}
            <Link className="secondary" href="#security-model">
              View security model
            </Link>
          </div>
        </div>
        <div className="trustCard">
          <div className="pulse" />
          <h3>Trust boundary active</h3>
          <dl>
            <div>
              <dt>Browser tokens</dt>
              <dd>Never exposed</dd>
            </div>
            <div>
              <dt>AI access</dt>
              <dd>60-second JWT</dd>
            </div>
            <div>
              <dt>Data at rest</dt>
              <dd>AES-256-GCM</dd>
            </div>
            <div>
              <dt>Answers</dt>
              <dd>Citation grounded</dd>
            </div>
          </dl>
        </div>
      </section>
      <section className="featureGrid" id="security-model">
        <article>
          <h3>Clinical evidence first</h3>
          <p>
            Every answer carries source document, page, chunk, and retrieval
            score.
          </p>
        </article>
        <article>
          <h3>Designed for isolation</h3>
          <p>
            Tenant identity is derived from verified tokens and checked
            independently in both services.
          </p>
        </article>
        <article>
          <h3>Private model boundary</h3>
          <p>
            Use an internally hosted model endpoint; public LLM egress is
            disabled by default.
          </p>
        </article>
      </section>
    </main>
  );
}
