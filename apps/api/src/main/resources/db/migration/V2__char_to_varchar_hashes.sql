-- Align hash columns with Hibernate String->varchar mapping (CHAR became bpchar mismatch under Hibernate 7).
ALTER TABLE clinical_document ALTER COLUMN sha256 TYPE VARCHAR(64);
ALTER TABLE audit_event ALTER COLUMN source_ip_hash TYPE VARCHAR(64);
