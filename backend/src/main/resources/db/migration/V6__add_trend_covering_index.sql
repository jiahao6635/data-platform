-- 覆盖索引：加速带 bucket/database/table 过滤的 trend 查询
-- 将 size_bytes 和 table_key 包含在索引中，支持 Index-Only Scan，
-- 避免回表读取 heap 数据
CREATE INDEX idx_asset_snapshot_trend_filtered
    ON asset_snapshot (batch_id, bucket, db_name, table_name)
    INCLUDE (size_bytes, table_key)
    WHERE scan_type = 'table';