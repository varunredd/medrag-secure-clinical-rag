"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";

import { AuditIcon } from "@/components/Icons";
import { apiProblem, clinicalFetch, supportMessage } from "@/lib/clinical-fetch";

type ExportRequest = {
  id: string;
  requestType: string;
  status: string;
  requestedBy: string;
  createdAt: string;
};

type ExportPage = { content: ExportRequest[] };

export function ComplianceRequestsClient() {
  const [requestType, setRequestType] = useState("DSAR");
  const [subjectReference, setSubjectReference] = useState("");
  const [requests, setRequests] = useState<ExportRequest[]>([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    const response = await clinicalFetch(
      "/api/backend/api/v1/export-requests?size=10",
    ).catch(() => null);
    if (response?.ok) {
      const body = (await response.json()) as ExportPage;
      setRequests(body.content);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setNotice(null);
    const response = await clinicalFetch(
      "/api/backend/api/v1/export-requests",
      {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ requestType, subjectReference }),
      },
    ).catch(() => null);
    setBusy(false);
    if (!response) return;
    if (!response.ok) {
      const problem = await apiProblem(response);
      setNotice(supportMessage(problem, "The compliance request could not be created."));
      return;
    }
    setSubjectReference("");
    setNotice("Request queued for privacy and legal review. No raw subject reference was stored.");
    await load();
  }

  return (
    <section className="panel settingsPanel fullWidth compliancePanel">
      <div className="panelHeader">
        <div>
          <p className="sectionKicker">PRIVACY OPERATIONS</p>
          <h2>Export and DSAR intake</h2>
        </div>
        <AuditIcon className="panelIcon" />
      </div>
      <div className="complianceGrid">
        <form onSubmit={submit} className="complianceForm">
          <label className="field">
            <span>Request type</span>
            <select value={requestType} onChange={(event) => setRequestType(event.target.value)}>
              <option value="DSAR">Data subject access request</option>
              <option value="DATA_EXPORT">Tenant data export</option>
            </select>
          </label>
          <label className="field">
            <span>Subject reference</span>
            <input
              required
              minLength={2}
              maxLength={500}
              value={subjectReference}
              onChange={(event) => setSubjectReference(event.target.value)}
              placeholder="Internal patient or case reference"
            />
            <small>
              The API normalizes and stores only a SHA-256 digest. Do not enter clinical narrative.
            </small>
          </label>
          <button type="submit" className="secondary" disabled={busy}>
            {busy ? "Creating request…" : "Create review request"}
          </button>
          {notice && <div className="notice info">{notice}</div>}
        </form>
        <div className="requestQueue">
          <div className="queueHeader">
            <strong>Recent requests</strong>
            <span>{requests.length} shown</span>
          </div>
          {requests.length === 0 ? (
            <p className="mutedCopy">No export or DSAR requests have been opened.</p>
          ) : (
            <ul>
              {requests.map((request) => (
                <li key={request.id}>
                  <div>
                    <strong>{request.requestType.replaceAll("_", " ")}</strong>
                    <small>{new Date(request.createdAt).toLocaleString()}</small>
                  </div>
                  <span>{request.status.replaceAll("_", " ")}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
      <p className="settingsFootnote">
        This is an intake and audit workflow, not an automatic export engine. Identity verification,
        approval, redaction, delivery, and deletion decisions remain human-controlled deployment procedures.
      </p>
    </section>
  );
}
