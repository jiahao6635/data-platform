ALTER TABLE asset_snapshot
    ADD COLUMN partition_name VARCHAR(1024) NOT NULL DEFAULT '',
    ADD COLUMN table_key CHAR(64);

UPDATE asset_snapshot
SET table_key = asset_key
WHERE table_key IS NULL;

ALTER TABLE asset_snapshot
    ALTER COLUMN table_key SET NOT NULL;

ALTER TABLE snapshot_batch
    ADD COLUMN partition_count INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_asset_snapshot_table_key
    ON asset_snapshot (table_key, batch_id);

CREATE INDEX idx_asset_snapshot_partition_lookup
    ON asset_snapshot (batch_id, bucket, db_name, table_name, partition_name)
    WHERE scan_type = 'table';

