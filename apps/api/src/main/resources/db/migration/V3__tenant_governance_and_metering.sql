ALTER TABLE clinical_document
  ADD COLUMN classification VARCHAR(32) NOT NULL DEFAULT 'PHI_RESTRICTED',
  ADD COLUMN legal_hold BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN retention_until TIMESTAMPTZ;
CREATE INDEX idx_document_retention_due
  ON clinical_document (retention_until)
  WHERE deleted_at IS NULL AND legal_hold = FALSE AND retention_until IS NOT NULL;

CREATE TABLE tenant_setting (
  tenant_id VARCHAR(120) PRIMARY KEY,
  clinic_name VARCHAR(160) NOT NULL,
  retention_days INTEGER CHECK (retention_days IS NULL OR retention_days BETWEEN 1 AND 36500),
  max_upload_bytes BIGINT NOT NULL CHECK (max_upload_bytes BETWEEN 1048576 AND 26214400),
  allowed_mime_types VARCHAR(600) NOT NULL,
  llm_mode VARCHAR(40) NOT NULL,
  llm_endpoint_ref VARCHAR(500),
  llm_secret_ref VARCHAR(500),
  llm_model VARCHAR(255),
  updated_by VARCHAR(120) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE usage_meter_event (
  id UUID PRIMARY KEY,
  tenant_id VARCHAR(120) NOT NULL,
  actor_id VARCHAR(120) NOT NULL,
  event_type VARCHAR(60) NOT NULL,
  quantity BIGINT NOT NULL CHECK (quantity >= 0),
  resource_id VARCHAR(120),
  request_id VARCHAR(100) NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_usage_meter_tenant_time ON usage_meter_event (tenant_id, occurred_at DESC);

CREATE TABLE export_request (
  id UUID PRIMARY KEY,
  tenant_id VARCHAR(120) NOT NULL,
  requested_by VARCHAR(120) NOT NULL,
  request_type VARCHAR(40) NOT NULL,
  subject_reference_hash VARCHAR(64),
  status VARCHAR(30) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_export_request_tenant_time ON export_request (tenant_id, created_at DESC);

CREATE OR REPLACE FUNCTION deny_usage_meter_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'usage_meter_event is append-only';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER usage_meter_event_no_update BEFORE UPDATE OR DELETE ON usage_meter_event
FOR EACH ROW EXECUTE FUNCTION deny_usage_meter_mutation();
