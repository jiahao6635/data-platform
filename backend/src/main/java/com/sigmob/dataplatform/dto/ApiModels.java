package com.sigmob.dataplatform.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ApiModels {

    private ApiModels() {
    }

    public record Summary(
            UUID batchId,
            OffsetDateTime snapshotAt,
            long totalSizeBytes,
            long bucketCount,
            long databaseCount,
            long tableCount,
            long partitionCount,
            long partitionedTableCount,
            long ownerCount,
            long zeroSizeTableCount,
            LocalDateTime latestModifiedAt,
            int rawRecordCount
    ) {
    }

    public record BucketMetric(String bucket, long sizeBytes, long tableCount, long zeroSizeTableCount) {
    }

    public record TopTable(
            String bucket,
            String database,
            String table,
            long sizeBytes,
            long partitionCount,
            LocalDateTime modTime,
            String owner
    ) {
    }

    public record OwnerMetric(String owner, long sizeBytes, long tableCount) {
    }

    public record TrendPoint(LocalDate date, long totalSizeBytes, long growthBytes, long tableCount) {
    }

    public record AssetItem(
            String tableKey,
            String bucket,
            String database,
            String table,
            long sizeBytes,
            long partitionCount,
            LocalDateTime modTime,
            LocalDateTime accessTime,
            String owner,
            String scanType,
            String collectHost,
            OffsetDateTime collectTime
    ) {
    }

    public record PartitionItem(
            long id,
            String partition,
            long sizeBytes,
            LocalDateTime modTime,
            LocalDateTime accessTime,
            String owner,
            String collectHost,
            OffsetDateTime collectTime
    ) {
    }

    public record Page<T>(List<T> items, long total, int page, int size) {
    }

    public record FilterOptions(
            List<String> buckets,
            List<String> databases,
            List<String> scanTypes,
            List<String> owners
    ) {
    }

    public record SnapshotItem(
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
            int errorCount
    ) {
    }

    public record ImportResult(
            UUID batchId,
            String status,
            int recordCount,
            int tableCount,
            int partitionCount,
            long totalTableSizeBytes,
            OffsetDateTime snapshotAt,
            boolean duplicate
    ) {
    }

    public record ErrorResponse(String code, String message, OffsetDateTime timestamp) {
    }
}
