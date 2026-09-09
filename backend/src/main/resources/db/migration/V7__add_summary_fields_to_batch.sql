-- 将 summary 查询需要的聚合字段预计算到 snapshot_batch，
-- 避免每次请求都扫描 asset_snapshot 全表。
ALTER TABLE snapshot_batch
    ADD COLUMN bucket_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN database_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN partitioned_table_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN owner_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN zero_size_table_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN latest_modified_at TIMESTAMP WITHOUT TIME ZONE;