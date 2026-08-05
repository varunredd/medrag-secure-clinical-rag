"use client";

import {
  ChangeEvent,
  DragEvent,
  FormEvent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import { DocumentIcon, UploadIcon } from "@/components/Icons";
import {
  apiProblem,
  clinicalFetch,
  supportMessage,
} from "@/lib/clinical-fetch";

type DocumentItem = {
  id: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  status: string;
  failureCode?: string;
  createdAt: string;
  classification: string;
  legalHold: boolean;
  retentionUntil?: string | null;
  updatedAt: string;
};

type Page<T> = {
  content: T[];
  totalElements: number;
};

type Props = {
  canUpload: boolean;
  canRetry: boolean;
  canDelete: boolean;
  canGovern: boolean;
};

type Notice = { kind: "success" | "error" | "info"; text: string } | null;
type UploadPolicy = { maxUploadBytes: number; allowedMimeTypes: string[] };

const DEFAULT_MAX_BYTES = 25 * 1024 * 1024;
const DEFAULT_ACCEPTED_TYPES = new Set([
  "application/pdf",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "text/plain",
]);
const ACCEPTED_EXTENSIONS = new Set(["pdf", "docx", "txt"]);

const FAILURE_HELP: Record<string, string> = {
  AI_UNAVAILABLE:
    "The private AI service could not be reached. Retry after checking service health.",
  AI_HTTP_409:
    "The AI index rejected a conflicting document state. An administrator should review the document lifecycle.",
  AI_HTTP_422:
    "Text extraction or document validation failed. Confirm the file is a supported, readable PDF, DOCX, or TXT.",
  AI_HTTP_503:
    "The AI service was busy or temporarily unavailable. Retry is safe.",
  LEASE_EXPIRED:
    "A worker stopped heartbeating before completion. The job was returned to the queue automatically.",
  DOCUMENT_PROCESSING_REJECTED:
    "The source failed secure extraction or integrity checks.",
};

export function DocumentsClient({ canUpload, canRetry, canDelete, canGovern }: Props) {
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [busy, setBusy] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [actionId, setActionId] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice>(null);
  const [policy, setPolicy] = useState<UploadPolicy>({
    maxUploadBytes: DEFAULT_MAX_BYTES,
    allowedMimeTypes: [...DEFAULT_ACCEPTED_TYPES],
  });
  const [filter, setFilter] = useState("ALL");
  const fileInput = useRef<HTMLInputElement>(null);

  const load = useCallback(async () => {
    const response = await clinicalFetch(
      "/api/backend/api/v1/documents?size=100",
    ).catch(() => null);
    if (!response) {
      return;
    }
    if (response.ok) {
      const body = (await response.json()) as Page<DocumentItem>;
      setDocuments(body.content);
      return;
    }
    const problem = await apiProblem(response);
    setNotice({
      kind: "error",
      text: supportMessage(problem, "Clinical records could not be loaded."),
    });
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void clinicalFetch("/api/backend/api/v1/upload-policy")
      .then(async (response) => {
        if (!response.ok) return;
        setPolicy((await response.json()) as UploadPolicy);
      })
      .catch(() => undefined);
  }, []);

  const hasActive = documents.some((document) =>
    ["QUEUED", "PROCESSING"].includes(document.status),
  );

  useEffect(() => {
    const timer = window.setInterval(
      () => void load(),
      hasActive ? 3_000 : 15_000,
    );
    return () => window.clearInterval(timer);
  }, [hasActive, load]);

  const filteredDocuments = useMemo(
    () =>
      filter === "ALL"
        ? documents
        : documents.filter((document) => document.status === filter),
    [documents, filter],
  );

  function chooseFile(file: File | null) {
    if (!file) {
      setSelectedFile(null);
      return;
    }
    const validation = validateFile(
      file,
      policy.maxUploadBytes,
      new Set(policy.allowedMimeTypes),
    );
    if (validation) {
      setSelectedFile(null);
      setNotice({ kind: "error", text: validation });
      if (fileInput.current) fileInput.current.value = "";
      return;
    }
    setSelectedFile(file);
    setNotice(null);
  }

  function onFileChange(event: ChangeEvent<HTMLInputElement>) {
    chooseFile(event.target.files?.[0] ?? null);
  }

  function onDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setDragging(false);
    chooseFile(event.dataTransfer.files?.[0] ?? null);
  }

  async function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedFile) {
      setNotice({ kind: "error", text: "Choose a PDF, DOCX, or TXT record first." });
      return;
    }

    setBusy(true);
    setUploadProgress(0);
    setNotice(null);
    const result = await uploadWithProgress(selectedFile, setUploadProgress);
    setBusy(false);

    if (result.status === 401) {
      const returnTo = window.location.pathname;
      window.location.assign(
        `/api/auth/login?reason=session_expired&returnTo=${encodeURIComponent(returnTo)}`,
      );
      return;
    }
    if (result.status >= 200 && result.status < 300) {
      setNotice({
        kind: "success",
        text: "Upload accepted. Malware scanning completed and secure ingestion is queued.",
      });
      setSelectedFile(null);
      setUploadProgress(0);
      if (fileInput.current) fileInput.current.value = "";
      await load();
      return;
    }

    const problem = parseProblem(result.body, result.requestId);
    setNotice({
      kind: "error",
      text: supportMessage(problem, "The clinical record could not be uploaded."),
    });
  }

  async function govern(
    documentId: string,
    kind: "legal-hold" | "classification",
    value: boolean | string,
  ) {
    setActionId(documentId);
    setNotice(null);
    const response = await clinicalFetch(
      `/api/backend/api/v1/documents/${documentId}/${kind}`,
      {
        method: "PATCH",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(
          kind === "legal-hold"
            ? { enabled: Boolean(value) }
            : { classification: String(value) },
        ),
      },
    ).catch(() => null);
    setActionId(null);
    if (!response) return;
    if (!response.ok) {
      const problem = await apiProblem(response);
      setNotice({
        kind: "error",
        text: supportMessage(problem, "The governance change failed."),
      });
      return;
    }
    setNotice({
      kind: "success",
      text:
        kind === "legal-hold"
          ? Boolean(value)
            ? "Legal hold applied. Retention and deletion are now blocked."
            : "Legal hold released. Normal retention policy applies again."
          : "Data classification updated and appended to the audit trail.",
    });
    await load();
  }

  async function act(documentId: string, action: "retry" | "delete") {
    if (
      action === "delete" &&
      !window.confirm(
        "Soft-delete this record and queue purge of its encrypted AI index data?",
      )
    ) {
      return;
    }
    setActionId(documentId);
    setNotice(null);
    const response = await clinicalFetch(
      `/api/backend/api/v1/documents/${documentId}${
        action === "retry" ? "/retry" : ""
      }`,
      { method: action === "retry" ? "POST" : "DELETE" },
    ).catch(() => null);
    setActionId(null);
    if (!response) {
      return;
    }
    if (!response.ok) {
      const problem = await apiProblem(response);
      setNotice({
        kind: "error",
        text: supportMessage(problem, "The document action failed."),
      });
    } else {
      setNotice({
        kind: "success",
        text:
          action === "retry"
            ? "The document has been returned to the secure ingestion queue."
            : "The record was soft-deleted and purge was queued.",
      });
    }
    await load();
  }

  const counts = statusCounts(documents);

  return (
    <>
      {canUpload ? (
        <section className="panel uploadPanel">
          <div className="uploadIntro">
            <div>
              <p className="sectionKicker">SECURE INGESTION</p>
              <h2>Upload clinical record</h2>
              <p>
                Files are validated and malware-scanned before private object persistence.
                Original filenames are not retained because they may contain PHI.
              </p>
            </div>
            <div className="uploadPolicy">
              <span>{policy.allowedMimeTypes.map(mimeLabel).join(" · ")}</span>
              <strong>{formatBytes(policy.maxUploadBytes)} maximum</strong>
            </div>
          </div>
          <form onSubmit={upload}>
            <div
              className={dragging ? "dropZone dragging" : "dropZone"}
              onDragEnter={(event) => {
                event.preventDefault();
                setDragging(true);
              }}
              onDragOver={(event) => event.preventDefault()}
              onDragLeave={() => setDragging(false)}
              onDrop={onDrop}
              onClick={() => fileInput.current?.click()}
              onKeyDown={(event) => {
                if (event.key === "Enter" || event.key === " ") {
                  event.preventDefault();
                  fileInput.current?.click();
                }
              }}
              role="button"
              tabIndex={0}
              aria-label="Choose or drop a clinical record"
            >
              <input
                ref={fileInput}
                name="file"
                type="file"
                hidden
                onChange={onFileChange}
                accept=".pdf,.docx,.txt,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain"
              />
              <div className="dropIcon"><UploadIcon /></div>
              {selectedFile ? (
                <div className="selectedFile">
                  <strong>{selectedFile.name}</strong>
                  <span>{formatBytes(selectedFile.size)} · Ready to upload</span>
                </div>
              ) : (
                <div>
                  <strong>Drop one record here or choose a file</strong>
                  <span>Client-side validation runs before transfer.</span>
                </div>
              )}
            </div>
            {busy && (
              <div className="uploadProgress" aria-live="polite">
                <div><span style={{ width: `${uploadProgress}%` }} /></div>
                <small>Encrypted session upload {uploadProgress}%</small>
              </div>
            )}
            <div className="uploadActions">
              <button
                type="submit"
                disabled={busy || !selectedFile}
                className="primary"
              >
                <UploadIcon /> {busy ? "Uploading securely…" : "Start secure upload"}
              </button>
              {selectedFile && !busy && (
                <button
                  type="button"
                  className="textAction"
                  onClick={() => chooseFile(null)}
                >
                  Clear selection
                </button>
              )}
            </div>
          </form>
        </section>
      ) : (
        <div className="notice info">
          Your current role has read-only access to clinical records. Upload controls are available to Doctor, Nurse, and Clinic Admin roles.
        </div>
      )}

      {notice && (
        <div className={`notice ${notice.kind}`} role="status">
          {notice.text}
        </div>
      )}

      <section className="panel recordsPanel">
        <div className="panelHeader documentsHeader">
          <div>
            <p className="sectionKicker">DOCUMENT VAULT</p>
            <h2>Clinical records</h2>
          </div>
          <div className="filterTabs" role="group" aria-label="Filter records by status">
            {[
              ["ALL", documents.length],
              ["READY", counts.READY],
              ["PROCESSING", counts.PROCESSING + counts.QUEUED],
              ["FAILED", counts.FAILED],
            ].map(([value, count]) => (
              <button
                type="button"
                key={String(value)}
                className={filter === value ? "active" : ""}
                onClick={() => setFilter(String(value))}
              >
                {value === "PROCESSING" ? "In progress" : titleCase(String(value))}
                <span>{count}</span>
              </button>
            ))}
          </div>
        </div>

        <div className="tableWrap">
          <table className="clinicalTable">
            <thead>
              <tr>
                <th>Record</th>
                <th>Processing status</th>
                <th>Size</th>
                <th>Updated</th>
                {canGovern && <th>Governance</th>}
                {(canRetry || canDelete) && <th><span className="srOnly">Actions</span></th>}
              </tr>
            </thead>
            <tbody>
              {filteredDocuments.map((document) => (
                <tr key={document.id}>
                  <td>
                    <div className="documentCell">
                      <div className="fileGlyph"><DocumentIcon /></div>
                      <div>
                        <strong>{document.filename}</strong>
                        <small>Record {shortId(document.id)} · {mimeLabel(document.contentType)}</small>
                        <div className="recordTags">
                          <span>{document.classification.replaceAll("_", " ")}</span>
                          {document.legalHold && <b>LEGAL HOLD</b>}
                          {document.retentionUntil && (
                            <span>Retain until {formatShortDate(document.retentionUntil)}</span>
                          )}
                        </div>
                      </div>
                    </div>
                  </td>
                  <td>
                    <div className="statusCell">
                      <span className={`status status-${document.status.toLowerCase()}`}>
                        {titleCase(document.status)}
                      </span>
                      <span>{statusDescription(document)}</span>
                      {["QUEUED", "PROCESSING"].includes(document.status) && (
                        <div className="processingBar" aria-label={`${document.status.toLowerCase()} in progress`}>
                          <span />
                        </div>
                      )}
                    </div>
                  </td>
                  <td>{formatBytes(document.sizeBytes)}</td>
                  <td>
                    <time dateTime={document.updatedAt}>{formatDate(document.updatedAt)}</time>
                  </td>
                  {canGovern && (
                    <td className="governanceCell">
                      <select
                        aria-label={`Classification for ${document.filename}`}
                        value={document.classification}
                        disabled={actionId === document.id}
                        onChange={(event) =>
                          void govern(document.id, "classification", event.target.value)
                        }
                      >
                        <option value="PHI_RESTRICTED">PHI restricted</option>
                        <option value="CLINICAL_CONFIDENTIAL">Clinical confidential</option>
                        <option value="DEIDENTIFIED">De-identified</option>
                        <option value="ADMINISTRATIVE">Administrative</option>
                      </select>
                      <button
                        type="button"
                        className={document.legalHold ? "holdButton active" : "holdButton"}
                        disabled={actionId === document.id}
                        onClick={() =>
                          void govern(document.id, "legal-hold", !document.legalHold)
                        }
                      >
                        {document.legalHold ? "Release hold" : "Apply hold"}
                      </button>
                    </td>
                  )}
                  {(canRetry || canDelete) && (
                    <td className="rowActions">
                      {canRetry && document.status === "FAILED" && (
                        <button
                          type="button"
                          className="secondary small"
                          disabled={actionId === document.id}
                          onClick={() => void act(document.id, "retry")}
                        >
                          {actionId === document.id ? "Working…" : "Retry"}
                        </button>
                      )}
                      {canDelete && (
                        <button
                          type="button"
                          className="dangerButton"
                          disabled={actionId === document.id || document.legalHold}
                          title={document.legalHold ? "Release legal hold before deletion" : undefined}
                          onClick={() => void act(document.id, "delete")}
                        >
                          Delete
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              ))}
              {filteredDocuments.length === 0 && (
                <tr>
                  <td colSpan={4 + (canGovern ? 1 : 0) + (canRetry || canDelete ? 1 : 0)}>
                    <div className="emptyState compact tableEmpty">
                      <DocumentIcon />
                      <strong>{documents.length ? "No records match this filter" : "No clinical records yet"}</strong>
                      <p>{documents.length ? "Choose another processing state." : "The secure document vault is empty."}</p>
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}

function validateFile(
  file: File,
  maxBytes: number,
  allowedMimeTypes: Set<string>,
): string | null {
  const extension = file.name.split(".").pop()?.toLowerCase() ?? "";
  const extensionMime =
    extension === "pdf"
      ? "application/pdf"
      : extension === "docx"
        ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        : extension === "txt"
          ? "text/plain"
          : undefined;
  const accepted =
    allowedMimeTypes.has(file.type) ||
    (extensionMime !== undefined && allowedMimeTypes.has(extensionMime));
  if (!accepted || !ACCEPTED_EXTENSIONS.has(extension)) {
    return "This clinic does not allow the selected record format.";
  }
  if (file.size <= 0) return "The selected file is empty.";
  if (file.size > maxBytes) {
    return `The selected file exceeds this clinic's ${formatBytes(maxBytes)} upload limit.`;
  }
  return null;
}

function uploadWithProgress(
  file: File,
  onProgress: (value: number) => void,
): Promise<{ status: number; body: string; requestId?: string }> {
  return new Promise((resolve) => {
    const request = new XMLHttpRequest();
    request.open("POST", "/api/backend/api/v1/documents");
    request.setRequestHeader("Accept", "application/json, application/problem+json");
    request.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        onProgress(Math.min(99, Math.round((event.loaded / event.total) * 100)));
      }
    };
    request.onload = () => {
      onProgress(100);
      resolve({
        status: request.status,
        body: request.responseText,
        requestId: request.getResponseHeader("x-request-id") ?? undefined,
      });
    };
    request.onerror = () =>
      resolve({ status: 0, body: "", requestId: undefined });
    const form = new FormData();
    form.set("file", file);
    request.send(form);
  });
}

function parseProblem(body: string, requestId?: string) {
  try {
    const parsed = JSON.parse(body) as { detail?: string; code?: string; requestId?: string };
    return { ...parsed, requestId: parsed.requestId ?? requestId };
  } catch {
    return { requestId };
  }
}

function statusCounts(documents: DocumentItem[]) {
  return documents.reduce(
    (counts, document) => {
      counts[document.status] = (counts[document.status] ?? 0) + 1;
      return counts;
    },
    { READY: 0, PROCESSING: 0, QUEUED: 0, FAILED: 0 } as Record<string, number>,
  );
}

function statusDescription(document: DocumentItem) {
  switch (document.status) {
    case "QUEUED":
      return "Waiting for a private ingestion worker.";
    case "PROCESSING":
      return "Extracting, encrypting chunks, and rebuilding the tenant index.";
    case "READY":
      return "Available for explicit evidence-scoped queries.";
    case "FAILED":
      return FAILURE_HELP[document.failureCode ?? ""] ??
        `Processing stopped with code ${document.failureCode ?? "UNCLASSIFIED_FAILURE"}.`;
    default:
      return "Lifecycle status updated.";
  }
}

function titleCase(value: string) {
  return value.charAt(0) + value.slice(1).toLowerCase().replaceAll("_", " ");
}

function formatBytes(bytes: number) {
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function formatShortDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(new Date(value));
}

function formatDate(value: string) {
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

function mimeLabel(value: string) {
  if (value === "application/pdf") return "PDF";
  if (value.includes("wordprocessingml")) return "DOCX";
  if (value === "text/plain") return "TXT";
  return "RECORD";
}
