"""initial AI schema
Revision ID: 0001
Revises:
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql
revision="0001"
down_revision=None
branch_labels=None
depends_on=None

def upgrade() -> None:
    op.create_table(
        "ai_document",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("tenant_id", sa.String(120), nullable=False),
        sa.Column("content_type", sa.String(150), nullable=False),
        sa.Column("source_sha256", sa.String(64), nullable=False),
        sa.Column("embedding_model", sa.String(255), nullable=False),
        sa.Column("status", sa.String(30), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.UniqueConstraint("tenant_id", "id", name="uq_ai_document_tenant_id"),
    )
    op.create_index("idx_ai_document_tenant_status", "ai_document", ["tenant_id", "status"])
    op.create_table(
        "clinical_chunk",
        sa.Column("id", sa.BigInteger(), sa.Identity(), primary_key=True),
        sa.Column("tenant_id", sa.String(120), nullable=False),
        sa.Column("document_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("page", sa.Integer(), nullable=False),
        sa.Column("ordinal", sa.Integer(), nullable=False),
        sa.Column("text_ciphertext", sa.LargeBinary(), nullable=False),
        sa.Column("text_nonce", sa.LargeBinary(), nullable=False),
        sa.Column("embedding_ciphertext", sa.LargeBinary(), nullable=False),
        sa.Column("embedding_nonce", sa.LargeBinary(), nullable=False),
        sa.Column("content_sha256", sa.String(64), nullable=False),
        sa.Column("active", sa.Boolean(), nullable=False, server_default=sa.true()),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(["document_id"], ["ai_document.id"], ondelete="CASCADE"),
        sa.UniqueConstraint("tenant_id", "document_id", "ordinal", name="uq_chunk_document_ordinal"),
    )
    op.create_index("idx_chunk_tenant_active", "clinical_chunk", ["tenant_id", "active"])
    op.create_index("idx_chunk_tenant_document", "clinical_chunk", ["tenant_id", "document_id"])

def downgrade() -> None:
    op.drop_table("clinical_chunk")
    op.drop_table("ai_document")
