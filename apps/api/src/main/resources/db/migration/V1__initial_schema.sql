CREATE TABLE clinical_document (
  id UUID PRIMARY KEY,
  tenant_id VARCHAR(120) NOT NULL,
  safe_filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(150) NOT NULL,
  size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
  sha256 CHAR(64) NOT NULL,
  object_key VARCHAR(500) NOT NULL,
  status VARCHAR(32) NOT NULL,
  failure_code VARCHAR(80),
  uploaded_by VARCHAR(120) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, object_key)
);
CREATE INDEX idx_document_tenant_created ON clinical_document (tenant_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_document_tenant_status ON clinical_document (tenant_id, status) WHERE deleted_at IS NULL;

CREATE TABLE ingestion_job (
  id UUID PRIMARY KEY,
  tenant_id VARCHAR(120) NOT NULL,
  document_id UUID NOT NULL REFERENCES clinical_document(id),
  operation VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ NOT NULL,
  last_error_code VARCHAR(80),
  locked_at TIMESTAMPTZ,
  locked_by VARCHAR(120),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_ingestion_job_due ON ingestion_job (status, next_attempt_at);
CREATE UNIQUE INDEX uq_ingestion_job_active ON ingestion_job (document_id, operation)
WHERE status IN ('PENDING', 'RUNNING');
CREATE INDEX idx_ingestion_job_lease ON ingestion_job (status, locked_at) WHERE status = 'RUNNING';

CREATE TABLE audit_event (
  id UUID PRIMARY KEY,
  tenant_id VARCHAR(120) NOT NULL,
  actor_id VARCHAR(120) NOT NULL,
  actor_roles VARCHAR(500) NOT NULL,
  action VARCHAR(80) NOT NULL,
  resource_type VARCHAR(80) NOT NULL,
  resource_id VARCHAR(120),
  outcome VARCHAR(20) NOT NULL,
  request_id VARCHAR(100) NOT NULL,
  source_ip_hash CHAR(64),
  metadata_json TEXT NOT NULL DEFAULT '{}',
  occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_audit_tenant_time ON audit_event (tenant_id, occurred_at DESC);

-- Prevent accidental audit mutation. The application DB role should not be granted DELETE/UPDATE in production.
CREATE OR REPLACE FUNCTION deny_audit_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'audit_event is append-only';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER audit_event_no_update BEFORE UPDATE OR DELETE ON audit_event
FOR EACH ROW EXECUTE FUNCTION deny_audit_mutation();
