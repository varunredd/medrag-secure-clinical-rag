"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { AlertIcon, AskIcon, DocumentIcon, UploadIcon } from "@/components/Icons";
import {
  apiProblem,
  clinicalFetch,
  supportMessage,
} from "@/lib/clinical-fetch";

type Overview = {
  clinicName: string;
  documents: {
    total: number;
    ready: number;
    queued: number;
    processing: number;
    failed: number;
  };
  pipeline: { pending: number; running: number; dead: number };
  recentDocuments: Array<{
    id: string;
    filename: string;
    status: string;
    failureCode?: string;
    sizeBytes: number;
    createdAt: string;
    updatedAt: string;
  }>;
  deadJobs: Array<{
    id: string;
    documentId: string;
    operation: string;
    attempts: number;
    lastErrorCode?: string;
    updatedAt: string;
  }>;
  generatedAt: string;
};

const empty: Overview = {
  clinicName: "Clinical workspace",
  documents: { total: 0, ready: 0, queued: 0, processing: 0, failed: 0 },
  pipeline: { pending: 0, running: 0, dead: 0 },
  recentDocuments: [],
  deadJobs: [],
  generatedAt: new Date(0).toISOString(),
};

export function OverviewClient({
  canUpload,
  canQuery,
  canAdmin,
}: {
  canUpload: boolean;
  canQuery: boolean;
  canAdmin: boolean;
}) {
  const [overview, setOverview] = useState<Overview>(empty);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");
  const [redriving, setRedriving] = useState<string | null>(null);

  const load = useCallback(async () => {
    const response = await clinicalFetch(
      "/api/backend/api/v1/operations/overview",
    ).catch(() => null);
    if (!response) {
      return;
    }
    if (response.ok) {
      setOverview((await response.json()) as Overview);
      setMessage("");
    } else {
      const problem = await apiProblem(response);
      setMessage(supportMessage(problem, "Operational status could not be loaded."));
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), 10_000);
    return () => window.clearInterval(timer);
  }, [load]);

  async function redrive(jobId: string) {
    setRedriving(jobId);
    const response = await clinicalFetch(
      `/api/backend/api/v1/operations/jobs/${jobId}/redrive`,
      { method: "POST" },
    ).catch(() => null);
    setRedriving(null);
    if (!response) {
      return;
    }
    if (!response.ok) {
      const problem = await apiProblem(response);
      setMessage(supportMessage(problem, "The ingestion job could not be redriven."));
      return;
    }
    setMessage("Dead-letter job returned to the processing queue.");
    await load();
  }

  const activePipeline = overview.pipeline.pending + overview.pipeline.running;
  const pipelineState = overview.pipeline.dead
    ? "Attention required"
    : activePipeline
      ? "Processing"
      : "Healthy";

  return (
    <>
      {message && <div className="notice" role="status">{message}</div>}
      <div className="clinicContext">
        <span>Active clinic</span>
        <strong>{overview.clinicName}</strong>
      </div>
      <section className="metricGrid" aria-label="Workspace status">
        <article className="metricCard">
          <div className="metricIcon"><DocumentIcon /></div>
          <div>
            <span>Ready evidence</span>
            <strong>{loading ? "—" : overview.documents.ready}</strong>
            <small>{overview.documents.total} records in this clinic</small>
          </div>
        </article>
        <article className="metricCard">
          <div className="metricIcon"><UploadIcon /></div>
          <div>
            <span>Ingestion pipeline</span>
            <strong>{pipelineState}</strong>
            <small>{activePipeline} queued or running</small>
          </div>
        </article>
        <article className={overview.documents.failed ? "metricCard warning" : "metricCard"}>
          <div className="metricIcon"><AlertIcon /></div>
          <div>
            <span>Failed records</span>
            <strong>{loading ? "—" : overview.documents.failed}</strong>
            <small>{overview.pipeline.dead} dead-letter jobs</small>
          </div>
        </article>
        <article className="metricCard">
          <div className="metricIcon"><AskIcon /></div>
          <div>
            <span>Evidence scope</span>
            <strong>Explicit only</strong>
            <small>1–20 ready records per query</small>
          </div>
        </article>
      </section>

      <div className="overviewGrid">
        <section className="panel recentPanel">
          <div className="panelHeader">
            <div>
              <p className="sectionKicker">RECENT ACTIVITY</p>
              <h2>Clinical records</h2>
            </div>
            <Link className="secondary small" href="/dashboard/documents">
              View all
            </Link>
          </div>
          {overview.recentDocuments.length === 0 ? (
            <div className="emptyState compact">
              <DocumentIcon />
              <strong>No clinical records yet</strong>
              <p>Upload a synthetic or approved record to start the secure ingestion path.</p>
              {canUpload && <Link className="primary small" href="/dashboard/documents">Upload first record</Link>}
            </div>
          ) : (
            <div className="recordList">
              {overview.recentDocuments.map((document) => (
                <Link
                  href="/dashboard/documents"
                  className="recordRow"
                  key={document.id}
                >
                  <div className="fileGlyph"><DocumentIcon /></div>
                  <div className="recordMeta">
                    <strong>{document.filename}</strong>
                    <span>{formatBytes(document.sizeBytes)} · {formatTime(document.createdAt)}</span>
                  </div>
                  <span className={`status status-${document.status.toLowerCase()}`}>
                    {humanStatus(document.status)}
                  </span>
                </Link>
              ))}
            </div>
          )}
        </section>

        <section className="panel pipelinePanel">
          <div className="panelHeader">
            <div>
              <p className="sectionKicker">PIPELINE</p>
              <h2>Ingestion health</h2>
            </div>
            <span className={overview.pipeline.dead ? "healthPill warning" : "healthPill"}>
              <span /> {pipelineState}
            </span>
          </div>
          <div className="pipelineTrack" aria-label="Ingestion stages">
            <PipelineStage label="Queued" value={overview.documents.queued} />
            <PipelineStage label="Processing" value={overview.documents.processing} />
            <PipelineStage label="Ready" value={overview.documents.ready} />
            <PipelineStage label="Failed" value={overview.documents.failed} danger />
          </div>
          <div className="workflowActions">
            {canUpload && (
              <Link href="/dashboard/documents" className="primary small">
                <UploadIcon /> Upload record
              </Link>
            )}
            {canQuery && overview.documents.ready > 0 && (
              <Link href="/dashboard/query" className="secondary small">
                <AskIcon /> Ask about ready records
              </Link>
            )}
          </div>
          <p className="refreshNote">Live status refreshes every 10 seconds.</p>
        </section>
      </div>

      {overview.deadJobs.length > 0 && (
        <section className="panel attentionPanel">
          <div className="panelHeader">
            <div>
              <p className="sectionKicker">OPERATOR ATTENTION</p>
              <h2>Dead-letter ingestion jobs</h2>
              <p>Review the failure code before redriving. Repeated failures may indicate malformed source data or unavailable dependencies.</p>
            </div>
          </div>
          <div className="deadJobList">
            {overview.deadJobs.map((job) => (
              <div className="deadJobRow" key={job.id}>
                <AlertIcon />
                <div>
                  <strong>{job.lastErrorCode ?? "UNCLASSIFIED_FAILURE"}</strong>
                  <span>Document {shortId(job.documentId)} · {job.operation} · {job.attempts} attempts</span>
                  <small>{formatTime(job.updatedAt)}</small>
                </div>
                {canAdmin ? (
                  <button
                    type="button"
                    className="secondary small"
                    disabled={redriving === job.id}
                    onClick={() => void redrive(job.id)}
                  >
                    {redriving === job.id ? "Redriving…" : "Redrive"}
                  </button>
                ) : (
                  <span className="roleHint">Clinic admin required</span>
                )}
              </div>
            ))}
          </div>
        </section>
      )}
    </>
  );
}

function PipelineStage({
  label,
  value,
  danger = false,
}: {
  label: string;
  value: number;
  danger?: boolean;
}) {
  return (
    <div className={danger && value ? "pipelineStage danger" : "pipelineStage"}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function humanStatus(status: string) {
  return status.charAt(0) + status.slice(1).toLowerCase();
}

function formatBytes(bytes: number) {
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}

function shortId(value: string) {
  return `${value.slice(0, 8)}…`;
}
