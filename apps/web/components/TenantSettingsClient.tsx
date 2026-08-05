"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";

import { AlertIcon, SettingsIcon } from "@/components/Icons";
import { apiProblem, clinicalFetch, supportMessage } from "@/lib/clinical-fetch";

type TenantSettings = {
  tenantId: string;
  clinicName: string;
  retentionDays: number | null;
  maxUploadBytes: number;
  allowedMimeTypes: string[];
  llmMode: "EXTRACTIVE" | "PLATFORM_PRIVATE" | "PRIVATE_OPENAI_COMPATIBLE";
  llmEndpointRef?: string | null;
  llmSecretRef?: string | null;
  llmModel?: string | null;
  updatedBy: string;
  updatedAt: string;
};

type Notice = { kind: "success" | "error" | "info"; text: string } | null;

const MIME_OPTIONS = [
  { value: "application/pdf", label: "PDF" },
  {
    value: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    label: "DOCX",
  },
  { value: "text/plain", label: "TXT" },
];

export function TenantSettingsClient() {
  const [settings, setSettings] = useState<TenantSettings | null>(null);
  const [retentionEnabled, setRetentionEnabled] = useState(false);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState<Notice>(null);

  useEffect(() => {
    const load = async () => {
      const response = await clinicalFetch(
        "/api/backend/api/v1/tenant-settings",
      ).catch(() => null);
      if (!response) return;
      if (!response.ok) {
        const problem = await apiProblem(response);
        setNotice({
          kind: "error",
          text: supportMessage(problem, "Clinic settings could not be loaded."),
        });
        return;
      }
      const body = (await response.json()) as TenantSettings;
      setSettings(body);
      setRetentionEnabled(body.retentionDays !== null);
    };
    void load();
  }, []);

  const uploadMegabytes = useMemo(
    () => (settings ? Math.round(settings.maxUploadBytes / 1024 / 1024) : 25),
    [settings],
  );

  function patch<K extends keyof TenantSettings>(key: K, value: TenantSettings[K]) {
    setSettings((current) => (current ? { ...current, [key]: value } : current));
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!settings) return;
    setSaving(true);
    setNotice(null);
    const response = await clinicalFetch(
      "/api/backend/api/v1/tenant-settings",
      {
        method: "PUT",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          clinicName: settings.clinicName,
          retentionDays: retentionEnabled ? settings.retentionDays ?? 365 : null,
          maxUploadBytes: settings.maxUploadBytes,
          allowedMimeTypes: settings.allowedMimeTypes,
          llmMode: settings.llmMode,
          llmEndpointRef:
            settings.llmMode === "PRIVATE_OPENAI_COMPATIBLE"
              ? settings.llmEndpointRef
              : null,
          llmSecretRef:
            settings.llmMode === "PRIVATE_OPENAI_COMPATIBLE"
              ? settings.llmSecretRef
              : null,
          llmModel:
            settings.llmMode === "PRIVATE_OPENAI_COMPATIBLE"
              ? settings.llmModel
              : null,
        }),
      },
    ).catch(() => null);
    setSaving(false);
    if (!response) return;
    if (!response.ok) {
      const problem = await apiProblem(response);
      setNotice({
        kind: "error",
        text: supportMessage(problem, "Clinic settings could not be saved."),
      });
      return;
    }
    const body = (await response.json()) as TenantSettings;
    setSettings(body);
    setRetentionEnabled(body.retentionDays !== null);
    setNotice({
      kind: "success",
      text: "Clinic governance settings saved and appended to the audit trail.",
    });
  }

  if (!settings) {
    return (
      <section className="panel settingsLoading" aria-busy="true">
        <span className="spinner" /> Loading tenant governance controls…
      </section>
    );
  }

  return (
    <form className="settingsGrid" onSubmit={save}>
      {notice && (
        <div className={`notice ${notice.kind} settingsNotice`} role="status">
          {notice.text}
        </div>
      )}

      <section className="panel settingsPanel">
        <div className="panelHeader">
          <div>
            <p className="sectionKicker">ORGANIZATION</p>
            <h2>Clinic identity and limits</h2>
          </div>
          <SettingsIcon className="panelIcon" />
        </div>
        <div className="formGrid">
          <label className="field full">
            <span>Clinic display name</span>
            <input
              value={settings.clinicName}
              minLength={2}
              maxLength={160}
              required
              onChange={(event) => patch("clinicName", event.target.value)}
            />
            <small>Light-touch tenant branding only; the tenant claim remains immutable.</small>
          </label>
          <label className="field">
            <span>Maximum upload size</span>
            <select
              value={uploadMegabytes}
              onChange={(event) =>
                patch("maxUploadBytes", Number(event.target.value) * 1024 * 1024)
              }
            >
              {[5, 10, 15, 20, 25].map((value) => (
                <option key={value} value={value}>{value} MB</option>
              ))}
            </select>
          </label>
          <fieldset className="field mimeField">
            <legend>Allowed record formats</legend>
            <div className="checkRow">
              {MIME_OPTIONS.map((option) => {
                const checked = settings.allowedMimeTypes.includes(option.value);
                return (
                  <label key={option.value}>
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={(event) => {
                        const next = event.target.checked
                          ? [...settings.allowedMimeTypes, option.value]
                          : settings.allowedMimeTypes.filter((value) => value !== option.value);
                        if (next.length) patch("allowedMimeTypes", next);
                      }}
                    />
                    <span>{option.label}</span>
                  </label>
                );
              })}
            </div>
          </fieldset>
        </div>
      </section>

      <section className="panel settingsPanel">
        <div className="panelHeader">
          <div>
            <p className="sectionKicker">RETENTION</p>
            <h2>Automated lifecycle policy</h2>
          </div>
          <span className={retentionEnabled ? "policyState enabled" : "policyState"}>
            {retentionEnabled ? "Enabled" : "Disabled"}
          </span>
        </div>
        <div className="policyCallout">
          <AlertIcon />
          <p>
            Automatic retention is deliberately opt-in. Legal hold always overrides expiry,
            and deletion remains a soft-delete followed by an auditable purge job.
          </p>
        </div>
        <label className="toggleRow">
          <input
            type="checkbox"
            checked={retentionEnabled}
            onChange={(event) => setRetentionEnabled(event.target.checked)}
          />
          <span>
            <strong>Enable automated retention</strong>
            <small>Apply only to records uploaded after this policy is saved.</small>
          </span>
        </label>
        <label className="field retentionField">
          <span>Retain records for</span>
          <div className="inputSuffix">
            <input
              type="number"
              min={1}
              max={36500}
              disabled={!retentionEnabled}
              value={settings.retentionDays ?? 365}
              onChange={(event) => patch("retentionDays", Number(event.target.value))}
            />
            <span>days</span>
          </div>
        </label>
      </section>

      <section className="panel settingsPanel fullWidth">
        <div className="panelHeader">
          <div>
            <p className="sectionKicker">PRIVATE GENERATION</p>
            <h2>Tenant model routing</h2>
          </div>
          <span className="privateBadge">No browser secrets</span>
        </div>
        <div className="modeSelector" role="radiogroup" aria-label="Generation mode">
          <label className={settings.llmMode === "EXTRACTIVE" ? "selected" : ""}>
            <input
              type="radio"
              name="llmMode"
              value="EXTRACTIVE"
              checked={settings.llmMode === "EXTRACTIVE"}
              onChange={() => patch("llmMode", "EXTRACTIVE")}
            />
            <strong>Grounded extractive fallback</strong>
            <span>Returns retrieved evidence without calling a generative model.</span>
          </label>
          <label className={settings.llmMode === "PLATFORM_PRIVATE" ? "selected" : ""}>
            <input
              type="radio"
              name="llmMode"
              value="PLATFORM_PRIVATE"
              checked={settings.llmMode === "PLATFORM_PRIVATE"}
              onChange={() => patch("llmMode", "PLATFORM_PRIVATE")}
            />
            <strong>Managed platform-private model</strong>
            <span>Uses the deployment-wide approved private endpoint; falls back visibly when unset.</span>
          </label>
          <label className={settings.llmMode === "PRIVATE_OPENAI_COMPATIBLE" ? "selected" : ""}>
            <input
              type="radio"
              name="llmMode"
              value="PRIVATE_OPENAI_COMPATIBLE"
              checked={settings.llmMode === "PRIVATE_OPENAI_COMPATIBLE"}
              onChange={() => patch("llmMode", "PRIVATE_OPENAI_COMPATIBLE")}
            />
            <strong>Tenant-specific private endpoint</strong>
            <span>FastAPI resolves endpoint and API key from vault references at query time.</span>
          </label>
        </div>
        {settings.llmMode === "PRIVATE_OPENAI_COMPATIBLE" && (
          <div className="formGrid llmFields">
            <label className="field">
              <span>Endpoint vault reference</span>
              <input
                placeholder="vault://medrag/clinic-demo/llm#endpoint"
                value={settings.llmEndpointRef ?? ""}
                required
                onChange={(event) => patch("llmEndpointRef", event.target.value)}
              />
            </label>
            <label className="field">
              <span>API secret vault reference</span>
              <input
                placeholder="vault://medrag/clinic-demo/llm#api-key"
                value={settings.llmSecretRef ?? ""}
                required
                onChange={(event) => patch("llmSecretRef", event.target.value)}
              />
            </label>
            <label className="field">
              <span>Model identifier</span>
              <input
                placeholder="clinical-private-model"
                value={settings.llmModel ?? ""}
                required
                onChange={(event) => patch("llmModel", event.target.value)}
              />
            </label>
          </div>
        )}
        <p className="settingsFootnote">
          Tenant-specific mode sends only vault references and the approved model identifier to
          FastAPI. FastAPI resolves them from Vault at query time; the browser and Spring database
          never receive the endpoint credential. Configure Vault authentication and deny-by-default
          model egress before enabling this mode in production.
        </p>
      </section>

      <div className="settingsActions">
        <div>
          <strong>Tenant {settings.tenantId}</strong>
          <span>Last updated {new Date(settings.updatedAt).toLocaleString()}</span>
        </div>
        <button type="submit" className="primary" disabled={saving}>
          {saving ? "Saving policy…" : "Save governance settings"}
        </button>
      </div>
    </form>
  );
}
