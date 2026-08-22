CREATE TABLE snapshot_batch (
    id UUID PRIMARY KEY,
    source VARCHAR(20) NOT NULL,
    source_name VARCHAR(512),
    source_checksum CHAR(64),
    status VARCHAR(20) NOT NULL,
    snapshot_at TIMESTAMP WITH TIME ZONE,
    first_received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    record_count INTEGER NOT NULL DEFAULT 0,
    table_count INTEGER NOT NULL DEFAULT 0,
    total_table_size_bytes BIGINT NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_snapshot_batch_status
        CHECK (status IN ('RECEIVING', 'VALIDATING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_snapshot_batch_source
        CHECK (source IN ('FILE', 'KAFKA'))
);

CREATE UNIQUE INDEX uk_snapshot_batch_source_checksum
    ON snapshot_batch (source_checksum)
    WHERE source_checksum IS NOT NULL;

CREATE INDEX idx_snapshot_batch_published
    ON snapshot_batch (status, snapshot_at DESC, published_at DESC);

CREATE TABLE asset_snapshot (
    id BIGSERIAL PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES snapshot_batch(id) ON DELETE CASCADE,
    asset_key CHAR(64) NOT NULL,
    row_hash CHAR(64) NOT NULL,
    bucket VARCHAR(255) NOT NULL,
    db_name VARCHAR(255) NOT NULL DEFAULT '',
    table_name VARCHAR(512) NOT NULL DEFAULT '',
    size_bytes BIGINT NOT NULL,
    mod_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    access_time TIMESTAMP WITHOUT TIME ZONE,
    owner_name VARCHAR(255) NOT NULL DEFAULT '',
    scan_type VARCHAR(32) NOT NULL,
    collect_host VARCHAR(512) NOT NULL DEFAULT '',
    collect_time TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_asset_snapshot_size CHECK (size_bytes >= 0),
    CONSTRAINT uk_asset_snapshot_batch_row UNIQUE (batch_id, row_hash)
);

CREATE INDEX idx_asset_snapshot_batch_type
    ON asset_snapshot (batch_id, scan_type);

CREATE INDEX idx_asset_snapshot_batch_bucket
    ON asset_snapshot (batch_id, bucket);

CREATE INDEX idx_asset_snapshot_asset_key
    ON asset_snapshot (asset_key, batch_id);

CREATE INDEX idx_asset_snapshot_table_lookup
    ON asset_snapshot (batch_id, bucket, db_name, table_name)
    WHERE scan_type = 'table';

CREATE TABLE ingestion_error (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(20) NOT NULL,
    raw_payload TEXT,
    error_message TEXT NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

