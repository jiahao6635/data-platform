-- 新增 file_count 字段：每个分区/目录包含的文件数量
-- 旧数据没有该字段，默认每个分区记录对应 1 个文件
ALTER TABLE asset_snapshot
    ADD COLUMN file_count INTEGER NOT NULL DEFAULT 1;

COMMENT ON COLUMN asset_snapshot.file_count IS '分区/目录内的文件数量';