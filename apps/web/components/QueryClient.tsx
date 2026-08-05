"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";

import { AskIcon, DocumentIcon } from "@/components/Icons";
import {
  apiProblem,
  clinicalFetch,
  supportMessage,
} from "@/lib/clinical-fetch";

type Citation = {
  documentId: string;
  page: number;
  chunkOrdinal: number;
  excerpt: string;
  score: number;
};

type Answer = {
  answer: string;
  citations: Citation[];
  confidence: number;
  embeddingModel: string;
  generationModel: string;
  disclaimer: string;
};

type DocumentItem = {
  id: string;
  filename: string;
  status: string;
  contentType?: string;
  createdAt: string;
};

type Page<T> = { content: T[] };

export function QueryClient() {
  const [answer, setAnswer] = useState<Answer | null>(null);
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [selected, setSelected] = useState<string[]>([]);
  const [question, setQuestion] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [citationIndex, setCitationIndex] = useState(0);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    void clinicalFetch("/api/backend/api/v1/documents?size=100")
      .then(async (response) => {
        if (!response.ok) {
          const problem = await apiProblem(response);
          throw new Error(supportMessage(problem, "Ready records could not be loaded."));
        }
        return response.json() as Promise<Page<DocumentItem>>;
      })
      .then((body) =>
        setDocuments(body.content.filter((item) => item.status === "READY")),
      )
      .catch((caught: unknown) =>
        setError(caught instanceof Error ? caught.message : "Ready records could not be loaded."),
      );
  }, []);

  const selectedDocuments = useMemo(
    () => documents.filter((document) => selected.includes(document.id)),
    [documents, selected],
  );

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (selected.length === 0) {
      setError("Select at least one ready clinical record before asking a question.");
      return;
    }

    setBusy(true);
    setError("");
    setAnswer(null);
    setCitationIndex(0);
    const response = await clinicalFetch("/api/backend/api/v1/queries", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ question, documentIds: selected, topK: 8 }),
    }).catch(() => null);
    setBusy(false);

    if (!response) return;
    if (response.ok) {
      setAnswer((await response.json()) as Answer);
      return;
    }
    const problem = await apiProblem(response);
    setError(supportMessage(problem, "MedRAG could not complete the evidence review."));
  }

  function toggle(documentId: string) {
    setSelected((current) =>
      current.includes(documentId)
        ? current.filter((id) => id !== documentId)
        : current.length < 20
          ? [...current, documentId]
          : current,
    );
  }

  async function copyAnswer() {
    if (!answer) return;
    await navigator.clipboard.writeText(exportText(answer, selectedDocuments, question));
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1800);
  }

  function exportAnswer() {
    if (!answer) return;
    const blob = new Blob([exportText(answer, selectedDocuments, question)], {
      type: "text/plain;charset=utf-8",
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `medrag-clinical-review-${new Date().toISOString().slice(0, 10)}.txt`;
    link.click();
    URL.revokeObjectURL(url);
  }

  const activeCitation = answer?.citations[citationIndex];
  const fallback = answer ? fallbackMessage(answer.generationModel) : null;

  return (
    <div className="queryWorkspace">
      <section className="panel evidencePanel">
        <div className="panelHeader">
          <div>
            <p className="sectionKicker">STEP 1 · EVIDENCE SCOPE</p>
            <h2>Select ready records</h2>
            <p>
              Only selected records are eligible for retrieval. Tenant-wide implicit search is disabled.
            </p>
          </div>
          <span className="selectionCount">{selected.length}/20 selected</span>
        </div>

        <div className="scopeToolbar">
          <button
            type="button"
            className="textAction"
            disabled={!documents.length}
            onClick={() => setSelected(documents.slice(0, 20).map((item) => item.id))}
          >
            Select all ready
          </button>
          <button
            type="button"
            className="textAction"
            disabled={!selected.length}
            onClick={() => setSelected([])}
          >
            Clear scope
          </button>
        </div>

        <div className="documentPicker" aria-label="Evidence scope">
          {documents.map((document) => {
            const checked = selected.includes(document.id);
            return (
              <label
                key={document.id}
                className={checked ? "documentOption selected" : "documentOption"}
              >
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={() => toggle(document.id)}
                />
                <span className="checkVisual" aria-hidden="true" />
                <div className="fileGlyph"><DocumentIcon /></div>
                <span>
                  <strong>{document.filename}</strong>
                  <small>Ready · {formatDate(document.createdAt)} · {shortId(document.id)}</small>
                </span>
              </label>
            );
          })}
          {documents.length === 0 && (
            <div className="emptyState compact">
              <DocumentIcon />
              <strong>No ready evidence is available</strong>
              <p>Upload a record and wait for the ingestion status to reach Ready.</p>
              <a className="secondary small" href="/dashboard/documents">Open clinical records</a>
            </div>
          )}
        </div>

        <form onSubmit={submit} className="queryForm">
          <div className="formLabelRow">
            <label htmlFor="clinical-question">Step 2 · Clinical question</label>
            <span>{question.length}/2000</span>
          </div>
          <textarea
            id="clinical-question"
            name="question"
            required
            minLength={3}
            maxLength={2000}
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            placeholder="Example: Summarize medication changes, including dates and the evidence supporting each change."
          />
          <div className="querySubmitRow">
            <button
              disabled={busy || selected.length === 0 || question.trim().length < 3}
              className="primary"
            >
              <AskIcon /> {busy ? "Reviewing selected evidence…" : "Ask MedRAG"}
            </button>
            <span>Human verification is required before clinical use.</span>
          </div>
        </form>
        {error && <div className="notice error" role="alert">{error}</div>}
      </section>

      <section className="panel answerPanel" aria-live="polite">
        <div className="panelHeader answerHeader">
          <div>
            <p className="sectionKicker">CLINICAL REVIEW OUTPUT</p>
            <h2>Evidence-grounded summary</h2>
          </div>
          {answer && (
            <div className="answerActions">
              <button type="button" className="secondary small" onClick={() => void copyAnswer()}>
                {copied ? "Copied" : "Copy with disclaimer"}
              </button>
              <button type="button" className="secondary small" onClick={exportAnswer}>
                Export review
              </button>
            </div>
          )}
        </div>

        {!answer ? (
          <div className="emptyState answerEmpty">
            <AskIcon />
            <strong>{busy ? "Reviewing evidence…" : "No review generated yet"}</strong>
            <p>
              Choose the exact records and ask a focused question. Every returned evidence excerpt remains visible for verification.
            </p>
          </div>
        ) : (
          <>
            {fallback && (
              <div className="modeBanner">
                <strong>{fallback.title}</strong>
                <span>{fallback.detail}</span>
              </div>
            )}
            <div className="answerSummaryMeta">
              <span className="confidencePill">
                Retrieval confidence {Math.round(answer.confidence * 100)}%
              </span>
              <span>{selected.length} scoped record{selected.length === 1 ? "" : "s"}</span>
              <span>{answer.citations.length} evidence excerpt{answer.citations.length === 1 ? "" : "s"}</span>
            </div>
            <div className="answerText">{answer.answer}</div>
            <div className="disclaimer">
              <strong>Clinical safety notice</strong>
              <span>{answer.disclaimer}</span>
            </div>

            <div className="citationWorkspace">
              <div className="citationList" role="tablist" aria-label="Evidence citations">
                <div className="citationListHeader">
                  <h3>Evidence citations</h3>
                  <span>{answer.citations.length}</span>
                </div>
                {answer.citations.map((citation, index) => (
                  <button
                    type="button"
                    role="tab"
                    aria-selected={citationIndex === index}
                    className={citationIndex === index ? "citationTab active" : "citationTab"}
                    key={`${citation.documentId}-${citation.chunkOrdinal}`}
                    onClick={() => setCitationIndex(index)}
                  >
                    <b>E{index + 1}</b>
                    <span>
                      <strong>Page {citation.page}</strong>
                      <small>{shortId(citation.documentId)} · score {citation.score.toFixed(3)}</small>
                    </span>
                  </button>
                ))}
              </div>
              <aside className="citationDrawer" aria-label="Selected citation excerpt">
                {activeCitation ? (
                  <>
                    <div className="citationDrawerHeader">
                      <div>
                        <span>Evidence E{citationIndex + 1}</span>
                        <strong>Page {activeCitation.page}</strong>
                      </div>
                      <span className="scoreBadge">{activeCitation.score.toFixed(3)}</span>
                    </div>
                    <blockquote>{activeCitation.excerpt}</blockquote>
                    <dl>
                      <div><dt>Record</dt><dd>{activeCitation.documentId}</dd></div>
                      <div><dt>Chunk</dt><dd>{activeCitation.chunkOrdinal}</dd></div>
                    </dl>
                  </>
                ) : (
                  <p>No evidence excerpt was returned.</p>
                )}
              </aside>
            </div>
            <footer className="modelMeta">
              <span>Embedding: {answer.embeddingModel}</span>
              <span>Generation: {answer.generationModel}</span>
            </footer>
          </>
        )}
      </section>
    </div>
  );
}


function fallbackMessage(generationModel: string) {
  if (!generationModel.startsWith("extractive")) return null;
  const messages: Record<string, { title: string; detail: string }> = {
    "extractive-tenant-policy": {
      title: "Evidence-only mode is required by clinic policy.",
      detail: "No generative model was called. MedRAG returned the highest-ranked excerpts for clinician review.",
    },
    "extractive-platform-model-unset": {
      title: "The managed private model is not configured.",
      detail: "MedRAG stayed inside the private boundary and returned evidence-only output instead of calling a public provider.",
    },
    "extractive-vault-resolution-unavailable": {
      title: "Tenant-private model credentials are unavailable.",
      detail: "Vault resolution failed or is not configured. Evidence retrieval succeeded and no model credential was exposed.",
    },
    "extractive-private-model-error": {
      title: "The approved private model could not complete the request.",
      detail: "MedRAG degraded to evidence-only output. Use the support code from any error and ask an operator to inspect model availability.",
    },
    "extractive-no-evidence": {
      title: "No relevant evidence was found.",
      detail: "Try a more specific question or select a different set of ready records. No generative model was called.",
    },
  };
  return messages[generationModel] ?? {
    title: "Evidence-only safety fallback is active.",
    detail: "MedRAG did not call a generative model and returned retrieved evidence for direct clinical review.",
  };
}

function exportText(answer: Answer, documents: DocumentItem[], question: string) {
  const evidence = answer.citations
    .map(
      (citation, index) =>
        `E${index + 1} | document=${citation.documentId} | page=${citation.page} | score=${citation.score.toFixed(4)}\n${citation.excerpt}`,
    )
    .join("\n\n");
  return [
    "MEDRAG CLINICAL REVIEW — AI-GENERATED, NOT A FINAL CLINICAL RECORD",
    `Generated: ${new Date().toISOString()}`,
    `Question: ${question}`,
    `Evidence scope: ${documents.map((document) => document.id).join(", ")}`,
    "",
    answer.answer,
    "",
    `DISCLAIMER: ${answer.disclaimer}`,
    "",
    "EVIDENCE",
    evidence,
    "",
    `Embedding model: ${answer.embeddingModel}`,
    `Generation model: ${answer.generationModel}`,
  ].join("\n");
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(new Date(value));
}

function shortId(value: string) {
  return `${value.slice(0, 8)}…`;
}
