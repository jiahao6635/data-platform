package com.sigmob.dataplatform.ingestion;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public record ParsedOssRecord(
        String bucket,
        String database,
        String table,
        String partition,
        long sizeBytes,
        LocalDateTime modTime,
        LocalDateTime accessTime,
        String owner,
        String scanType,
        String collectHost,
        OffsetDateTime collectTime,
        String tableKey,
        String assetKey,
        String rowHash
) {
}
