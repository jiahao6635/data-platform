package com.sigmob.dataplatform.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import com.sigmob.dataplatform.config.AppProperties;
import com.sigmob.dataplatform.dto.ApiModels;
import com.sigmob.dataplatform.ingestion.ParsedOssRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SnapshotRepository {

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "sizeBytes", "size_bytes",
            "modTime", "mod_time",
            "bucket", "bucket",
            "database", "db_name",
            "table", "table_name",
            "owner", "owner_name",
            "partitionCount", "partition_count",
            "collectTime", "collect_time");

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final ZoneId zoneId;

    public SnapshotRepository(
            JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedJdbcTemplate,
            AppProperties properties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.zoneId = properties.zoneId();
    }

    public UUID createBatch(String source, String sourceName, String checksum) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO snapshot_batch (
                    id, source, source_name, source_checksum, status,
                    first_received_at, last_received_at
                ) VALUES (?, ?, ?, ?, 'RECEIVING', ?, ?)
                """, id, source, sourceName, checksum, now, now);
        return id;
    }

    public Optional<BatchState> findPublishedByChecksum(String checksum) {
        return namedJdbcTemplate.query("""
                        SELECT id, source, source_name, status, snapshot_at, published_at,
                               record_count, table_count, partition_count, total_table_size_bytes,
                               first_received_at, last_received_at
                        FROM snapshot_batch
                        WHERE source_checksum = :checksum AND status = 'PUBLISHED'
                        """,
                Map.of("checksum", checksum),
                batchStateMapper()).stream().findFirst();
    }

    public Optional<BatchState> findLatestReceivingKafkaBatch() {
        return jdbcTemplate.query("""
                        SELECT id, source, source_name, status, snapshot_at, published_at,
                               record_count, table_count, partition_count, total_table_size_bytes,
                               first_received_at, last_received_at
                        FROM snapshot_batch
                        WHERE source = 'KAFKA' AND status = 'RECEIVING'
                        ORDER BY created_at DESC
                        LIMIT 1
                        """, batchStateMapper())
                .stream().findFirst();
    }

    public Optional<BatchState> findBatch(UUID batchId) {
        return jdbcTemplate.query("""
                        SELECT id, source, source_name, status, snapshot_at, published_at,
                               record_count, table_count, partition_count, total_table_size_bytes,
                               first_received_at, last_received_at
                        FROM snapshot_batch
                        WHERE id = ?
                        """, batchStateMapper(), batchId)
                .stream().findFirst();
    }

    public int insertRecord(UUID batchId, ParsedOssRecord record) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO asset_snapshot (
                    batch_id, table_key, asset_key, row_hash, bucket, db_name, table_name, partition_name,
                    size_bytes, file_count, mod_time, access_time, owner_name, scan_type,
                    collect_host, collect_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (batch_id, row_hash) DO NOTHING
                """,
                batchId,
                record.tableKey(),
                record.assetKey(),
                record.rowHash(),
                record.bucket(),
                record.database(),
                record.table(),
                record.partition(),
                record.sizeBytes(),
                record.fileCount(),
                record.modTime(),
                record.accessTime(),
                record.owner(),
                record.scanType(),
                record.collectHost(),
                record.collectTime());

        jdbcTemplate.update("""
                UPDATE snapshot_batch
                SET last_received_at = ?, status = 'RECEIVING'
                WHERE id = ? AND status = 'RECEIVING'
                """, OffsetDateTime.now(), batchId);
        return inserted;
    }

    public void publishBatch(UUID batchId) {
        int updated = jdbcTemplate.update("""
                UPDATE snapshot_batch b
                SET status = 'PUBLISHED',
                    snapshot_at = stats.snapshot_at,
                    published_at = CURRENT_TIMESTAMP,
                    record_count = stats.record_count,
                    table_count = stats.table_count,
                    partition_count = stats.partition_count,
                    total_table_size_bytes = stats.total_table_size_bytes,
                    bucket_count = stats.bucket_count,
                    database_count = stats.database_count,
                    partitioned_table_count = stats.partitioned_table_count,
                    owner_count = stats.owner_count,
                    latest_modified_at = stats.latest_modified_at
                FROM (
                    SELECT batch_id,
                           MAX(collect_time) AS snapshot_at,
                           COUNT(*) AS record_count,
                           COUNT(DISTINCT table_key) FILTER (WHERE scan_type = 'table') AS table_count,
                           COUNT(*) FILTER (WHERE scan_type = 'table' AND partition_name <> '') AS partition_count,
                           COALESCE(SUM(size_bytes) FILTER (WHERE scan_type = 'table'), 0) AS total_table_size_bytes,
                           COUNT(DISTINCT bucket) FILTER (WHERE scan_type = 'table') AS bucket_count,
                           COUNT(DISTINCT (bucket, db_name)) FILTER (WHERE scan_type = 'table' AND db_name <> '') AS database_count,
                           COUNT(DISTINCT table_key) FILTER (WHERE scan_type = 'table' AND partition_name <> '') AS partitioned_table_count,
                           COUNT(DISTINCT owner_name) FILTER (WHERE scan_type = 'table' AND owner_name <> '') AS owner_count,
                           MAX(mod_time) FILTER (WHERE scan_type = 'table') AS latest_modified_at
                    FROM asset_snapshot
                    WHERE batch_id = ?
                    GROUP BY batch_id
                ) stats
                WHERE b.id = stats.batch_id AND b.id = ? AND b.status IN ('RECEIVING', 'VALIDATING')
                """, batchId, batchId);
        if (updated != 1) {
            throw new IllegalStateException("快照为空、已发布或不存在: " + batchId);
        }

        // zero_size_table_count 需要先按 table_key 聚合再过滤，单独计算
        Integer zeroSizeCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT 1
                    FROM asset_snapshot
                    WHERE batch_id = ? AND scan_type = 'table'
                    GROUP BY table_key
                    HAVING SUM(size_bytes) = 0
                ) zero_tables
                """, Integer.class, batchId);
        jdbcTemplate.update(
                "UPDATE snapshot_batch SET zero_size_table_count = ? WHERE id = ?",
                zeroSizeCount != null ? zeroSizeCount : 0, batchId);
    }

    public void markFailed(UUID batchId) {
        jdbcTemplate.update("""
                UPDATE snapshot_batch
                SET status = 'FAILED', published_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status <> 'PUBLISHED'
                """, batchId);
    }

    public void saveIngestionError(String source, String rawPayload, String message) {
        jdbcTemplate.update("""
                INSERT INTO ingestion_error (source, raw_payload, error_message)
                VALUES (?, ?, ?)
                """, source, rawPayload, message);
    }

    public Optional<BatchState> findLatestPublishedBatch() {
        return jdbcTemplate.query("""
                        SELECT id, source, source_name, status, snapshot_at, published_at,
                               record_count, table_count, partition_count, total_table_size_bytes,
                               first_received_at, last_received_at
                        FROM snapshot_batch
                        WHERE status = 'PUBLISHED'
                        ORDER BY snapshot_at DESC, published_at DESC
                        LIMIT 1
                        """, batchStateMapper())
                .stream().findFirst();
    }

    public ApiModels.Summary loadSummary() {
        Optional<BatchState> latest = findLatestPublishedBatch();
        if (latest.isEmpty()) {
            return new ApiModels.Summary(null, null, 0, 0, 0, 0, 0, 0, 0, 0, null, 0);
        }
        BatchState batch = latest.get();
        return jdbcTemplate.queryForObject("""
                SELECT total_table_size_bytes,
                       bucket_count,
                       database_count,
                       table_count,
                       partition_count,
                       partitioned_table_count,
                       owner_count,
                       zero_size_table_count,
                       latest_modified_at,
                       record_count
                FROM snapshot_batch
                WHERE id = ?
                """, (resultSet, rowNum) -> new ApiModels.Summary(
                        batch.id(),
                        batch.snapshotAt(),
                        resultSet.getLong("total_table_size_bytes"),
                        resultSet.getLong("bucket_count"),
                        resultSet.getLong("database_count"),
                        resultSet.getLong("table_count"),
                        resultSet.getLong("partition_count"),
                        resultSet.getLong("partitioned_table_count"),
                        resultSet.getLong("owner_count"),
                        resultSet.getLong("zero_size_table_count"),
                        resultSet.getObject("latest_modified_at", LocalDateTime.class),
                        resultSet.getInt("record_count")),
                batch.id());
    }

    public List<ApiModels.BucketMetric> loadBucketMetrics() {
        Optional<BatchState> latest = findLatestPublishedBatch();
        if (latest.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query("""
                WITH table_rollup AS (
                    SELECT bucket, table_key, SUM(size_bytes) AS size_bytes
                    FROM asset_snapshot
                    WHERE batch_id = ? AND scan_type = 'table'
                    GROUP BY bucket, table_key
                )
                SELECT bucket, SUM(size_bytes) AS size_bytes, COUNT(*) AS table_count,
                       COUNT(*) FILTER (WHERE size_bytes = 0) AS zero_size_count
                FROM table_rollup
                GROUP BY bucket
                ORDER BY size_bytes DESC
                """, (resultSet, rowNum) -> new ApiModels.BucketMetric(
                        resultSet.getString("bucket"),
                        resultSet.getLong("size_bytes"),
                        resultSet.getLong("table_count"),
                        resultSet.getLong("zero_size_count")), latest.get().id());
    }

    public List<ApiModels.TopTable> loadTopTables(int limit) {
        Optional<BatchState> latest = findLatestPublishedBatch();
        if (latest.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT bucket, db_name, table_name,
                       SUM(size_bytes) AS size_bytes,
                       COUNT(*) FILTER (WHERE partition_name <> '') AS partition_count,
                       MAX(mod_time) AS mod_time,
                       MAX(owner_name) AS owner_name
                FROM asset_snapshot
                WHERE batch_id = ? AND scan_type = 'table'
                GROUP BY table_key, bucket, db_name, table_name
                ORDER BY size_bytes DESC
                LIMIT ?
                """, (resultSet, rowNum) -> new ApiModels.TopTable(
                        resultSet.getString("bucket"),
                        resultSet.getString("db_name"),
                        resultSet.getString("table_name"),
                        resultSet.getLong("size_bytes"),
                        resultSet.getLong("partition_count"),
                        resultSet.getObject("mod_time", LocalDateTime.class),
                        resultSet.getString("owner_name")), latest.get().id(), limit);
    }

    public List<ApiModels.OwnerMetric> loadOwnerMetrics(int limit) {
        Optional<BatchState> latest = findLatestPublishedBatch();
        if (latest.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query("""
                WITH table_rollup AS (
                    SELECT table_key, MAX(owner_name) AS owner_name, SUM(size_bytes) AS size_bytes
                    FROM asset_snapshot
                    WHERE batch_id = ? AND scan_type = 'table'
                    GROUP BY table_key
                )
                SELECT owner_name, SUM(size_bytes) AS size_bytes, COUNT(*) AS table_count
                FROM table_rollup
                WHERE owner_name <> ''
                GROUP BY owner_name
                ORDER BY size_bytes DESC
                LIMIT ?
                """, (resultSet, rowNum) -> new ApiModels.OwnerMetric(
                        resultSet.getString("owner_name"),
                        resultSet.getLong("size_bytes"),
                        resultSet.getLong("table_count")), latest.get().id(), limit);
    }

    public List<ApiModels.TrendPoint> loadTrend(String bucket, String database, String table, int days) {
        Instant from = Instant.now().minusSeconds(days * 86_400L);
        OffsetDateTime fromTime = OffsetDateTime.ofInstant(from, ZoneOffset.UTC);

        boolean hasFilter = (bucket != null && !bucket.isBlank())
                || (database != null && !database.isBlank())
                || (table != null && !table.isBlank());

        List<RawTrend> raw;
        if (hasFilter) {
            raw = loadTrendWithFilter(bucket, database, table, fromTime);
        } else {
            raw = loadTrendFromBatch(fromTime);
        }

        TreeMap<LocalDate, RawTrend> latestPerDay = new TreeMap<>();
        for (RawTrend point : raw) {
            latestPerDay.put(point.snapshotAt().atZoneSameInstant(zoneId).toLocalDate(), point);
        }

        List<ApiModels.TrendPoint> result = new ArrayList<>();
        Long previous = null;
        for (Map.Entry<LocalDate, RawTrend> entry : latestPerDay.entrySet()) {
            RawTrend point = entry.getValue();
            long growth = previous == null ? 0 : point.totalSizeBytes() - previous;
            result.add(new ApiModels.TrendPoint(entry.getKey(), point.totalSizeBytes(), growth, point.tableCount()));
            previous = point.totalSizeBytes();
        }
        return result;
    }

    /**
     * 无过滤条件时直接从 snapshot_batch 读取预聚合数据，避免 JOIN asset_snapshot。
     * snapshot_batch 在 publishBatch 时已计算好 total_table_size_bytes 和 table_count。
     */
    private List<RawTrend> loadTrendFromBatch(OffsetDateTime fromTime) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("from", fromTime);
        return namedJdbcTemplate.query("""
                SELECT snapshot_at,
                       total_table_size_bytes,
                       table_count
                FROM snapshot_batch
                WHERE status = 'PUBLISHED' AND snapshot_at >= :from
                ORDER BY snapshot_at
                """, parameters, (resultSet, rowNum) -> new RawTrend(
                        resultSet.getObject("snapshot_at", OffsetDateTime.class),
                        resultSet.getLong("total_table_size_bytes"),
                        resultSet.getLong("table_count")));
    }

    /**
     * 有 bucket/database/table 过滤条件时仍需 JOIN asset_snapshot，
     * 使用 idx_asset_snapshot_trend_filtered 索引加速。
     */
    private List<RawTrend> loadTrendWithFilter(
            String bucket, String database, String table, OffsetDateTime fromTime) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("from", fromTime);
        StringBuilder filter = new StringBuilder();
        if (bucket != null && !bucket.isBlank()) {
            filter.append(" AND a.bucket = :bucket");
            parameters.addValue("bucket", bucket.trim());
        }
        if (database != null && !database.isBlank()) {
            filter.append(" AND a.db_name = :database");
            parameters.addValue("database", database.trim());
        }
        if (table != null && !table.isBlank()) {
            filter.append(" AND a.table_name = :table");
            parameters.addValue("table", table.trim());
        }

        return namedJdbcTemplate.query("""
                SELECT b.snapshot_at,
                       COALESCE(SUM(a.size_bytes), 0) AS total_size_bytes,
                       COUNT(DISTINCT a.table_key) AS table_count
                FROM snapshot_batch b
                JOIN asset_snapshot a ON a.batch_id = b.id AND a.scan_type = 'table'
                WHERE b.status = 'PUBLISHED' AND b.snapshot_at >= :from
                """ + filter + "\n"
                + "GROUP BY b.id, b.snapshot_at\n"
                + "ORDER BY b.snapshot_at"
                , parameters, (resultSet, rowNum) -> new RawTrend(
                        resultSet.getObject("snapshot_at", OffsetDateTime.class),
                        resultSet.getLong("total_size_bytes"),
                        resultSet.getLong("table_count")));
    }

    public ApiModels.Page<ApiModels.AssetItem> loadAssets(
            String bucket,
            String database,
            String scanType,
            String owner,
            String keyword,
            int page,
            int size,
            String sort,
            String direction
    ) {
        Optional<BatchState> latest = findLatestPublishedBatch();
        if (latest.isEmpty()) {
            return new ApiModels.Page<>(List.of(), 0, page, size);
        }

        StringBuilder where = new StringBuilder(" WHERE batch_id = :batchId");
        MapSqlParameterSource parameters = new MapSqlParameterSource("batchId", latest.get().id());
        appendEqualsFilter(where, parameters, "bucket", "bucket", bucket);
        appendEqualsFilter(where, parameters, "db_name", "database", database);
        appendEqualsFilter(where, parameters, "scan_type", "scanType", scanType);
        appendEqualsFilter(where, parameters, "owner_name", "owner", owner);
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (LOWER(table_name) LIKE :keyword OR LOWER(db_name) LIKE :keyword OR LOWER(bucket) LIKE :keyword)");
            parameters.addValue("keyword", "%" + keyword.trim().toLowerCase() + "%");
        }

        long total = namedJdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT table_key) FROM asset_snapshot" + where,
                parameters,
                Long.class);

        String sortColumn = SORT_COLUMNS.getOrDefault(sort, "size_bytes");
        String sortDirection = "asc".equalsIgnoreCase(direction) ? "ASC" : "DESC";
        parameters.addValue("limit", size);
        parameters.addValue("offset", page * size);

        List<ApiModels.AssetItem> items = namedJdbcTemplate.query("""
                        SELECT table_key, bucket, db_name, table_name,
                               SUM(size_bytes) AS size_bytes,
                               COUNT(*) FILTER (WHERE partition_name <> '') AS partition_count,
                               SUM(file_count) AS file_count,
                               SUM(size_bytes) / NULLIF(SUM(file_count), 0) AS avg_file_size,
                               MAX(mod_time) AS mod_time,
                               MAX(access_time) AS access_time,
                               MAX(owner_name) AS owner_name,
                               MAX(scan_type) AS scan_type,
                               MAX(collect_host) AS collect_host,
                               MAX(collect_time) AS collect_time
                        FROM asset_snapshot
                        """ + where + " GROUP BY table_key, bucket, db_name, table_name ORDER BY "
                        + sortColumn + " " + sortDirection + ", table_key ASC LIMIT :limit OFFSET :offset",
                parameters,
                (resultSet, rowNum) -> new ApiModels.AssetItem(
                        resultSet.getString("table_key"),
                        resultSet.getString("bucket"),
                        resultSet.getString("db_name"),
                        resultSet.getString("table_name"),
                        resultSet.getLong("size_bytes"),
                        resultSet.getLong("partition_count"),
                        resultSet.getLong("file_count"),
                        resultSet.getLong("avg_file_size"),
                        resultSet.getObject("mod_time", LocalDateTime.class),
                        resultSet.getObject("access_time", LocalDateTime.class),
                        resultSet.getString("owner_name"),
                        resultSet.getString("scan_type"),
                        resultSet.getString("collect_host"),
                        resultSet.getObject("collect_time", OffsetDateTime.class)));

        return new ApiModels.Page<>(items, total, page, size);
    }

    public ApiModels.Page<ApiModels.PartitionItem> loadPartitions(
            String bucket,
            String database,
            String table,
            int page,
            int size
    ) {
        Optional<BatchState> latest = findLatestPublishedBatch();
        if (latest.isEmpty()) {
            return new ApiModels.Page<>(List.of(), 0, page, size);
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("batchId", latest.get().id())
                .addValue("bucket", bucket)
                .addValue("database", database)
                .addValue("table", table)
                .addValue("limit", size)
                .addValue("offset", page * size);

        String where = """
                WHERE batch_id = :batchId
                  AND scan_type = 'table'
                  AND bucket = :bucket
                  AND db_name = :database
                  AND table_name = :table
                  AND partition_name <> ''
                """;

        long total = namedJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM asset_snapshot " + where,
                parameters,
                Long.class);

        List<ApiModels.PartitionItem> items = namedJdbcTemplate.query("""
                        SELECT id, partition_name, size_bytes, mod_time, access_time,
                               owner_name, collect_host, collect_time
                        FROM asset_snapshot
                        """ + where + " ORDER BY partition_name DESC LIMIT :limit OFFSET :offset",
                parameters,
                (resultSet, rowNum) -> new ApiModels.PartitionItem(
                        resultSet.getLong("id"),
                        resultSet.getString("partition_name"),
                        resultSet.getLong("size_bytes"),
                        resultSet.getObject("mod_time", LocalDateTime.class),
                        resultSet.getObject("access_time", LocalDateTime.class),
                        resultSet.getString("owner_name"),
                        resultSet.getString("collect_host"),
                        resultSet.getObject("collect_time", OffsetDateTime.class)));

        return new ApiModels.Page<>(items, total, page, size);
    }

    public ApiModels.FilterOptions loadFilterOptions() {
        Optional<BatchState> latest = findLatestPublishedBatch();
        if (latest.isEmpty()) {
            return new ApiModels.FilterOptions(List.of(), List.of(), List.of(), List.of());
        }
        UUID batchId = latest.get().id();
        return new ApiModels.FilterOptions(
                loadDistinct(batchId, "bucket", false),
                loadDistinct(batchId, "db_name", true),
                loadDistinct(batchId, "scan_type", true),
                loadDistinct(batchId, "owner_name", true));
    }

    public List<ApiModels.SnapshotItem> loadSnapshots(int limit) {
        return jdbcTemplate.query("""
                SELECT id, source, source_name, status, snapshot_at, published_at,
                       record_count, table_count, partition_count, total_table_size_bytes, error_count
                FROM snapshot_batch
                ORDER BY created_at DESC
                LIMIT ?
                """, (resultSet, rowNum) -> new ApiModels.SnapshotItem(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("source"),
                        resultSet.getString("source_name"),
                        resultSet.getString("status"),
                        resultSet.getObject("snapshot_at", OffsetDateTime.class),
                        resultSet.getObject("published_at", OffsetDateTime.class),
                        resultSet.getInt("record_count"),
                        resultSet.getInt("table_count"),
                        resultSet.getInt("partition_count"),
                        resultSet.getLong("total_table_size_bytes"),
                        resultSet.getInt("error_count")), limit);
    }

    private List<String> loadDistinct(UUID batchId, String column, boolean excludeBlank) {
        String sql = "SELECT DISTINCT " + column + " AS value FROM asset_snapshot WHERE batch_id = ?";
        if (excludeBlank) {
            sql += " AND " + column + " <> ''";
        }
        sql += " ORDER BY value";
        return jdbcTemplate.query(sql, (resultSet, rowNum) -> resultSet.getString("value"), batchId);
    }

    private void appendEqualsFilter(
            StringBuilder where,
            MapSqlParameterSource parameters,
            String column,
            String parameter,
            String value
    ) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(column).append(" = :").append(parameter);
            parameters.addValue(parameter, value.trim());
        }
    }

    private RowMapper<BatchState> batchStateMapper() {
        return (resultSet, rowNum) -> new BatchState(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("source"),
                resultSet.getString("source_name"),
                resultSet.getString("status"),
                resultSet.getObject("snapshot_at", OffsetDateTime.class),
                resultSet.getObject("published_at", OffsetDateTime.class),
                resultSet.getInt("record_count"),
                resultSet.getInt("table_count"),
                resultSet.getInt("partition_count"),
                resultSet.getLong("total_table_size_bytes"),
                resultSet.getObject("first_received_at", OffsetDateTime.class),
                resultSet.getObject("last_received_at", OffsetDateTime.class));
    }

    private record RawTrend(OffsetDateTime snapshotAt, long totalSizeBytes, long tableCount) {
    }

    public record BatchState(
            UUID id,
            String source,
            String sourceName,
            String status,
            OffsetDateTime snapshotAt,
            OffsetDateTime publishedAt,
            int recordCount,
            int tableCount,
            int partitionCount,
            long totalTableSizeBytes,
            OffsetDateTime firstReceivedAt,
            OffsetDateTime lastReceivedAt
    ) {
        public ApiModels.ImportResult toImportResult(boolean duplicate) {
            return new ApiModels.ImportResult(
                    id,
                    status,
                    recordCount,
                    tableCount,
                    partitionCount,
                    totalTableSizeBytes,
                    snapshotAt,
                    duplicate);
        }
    }
}
