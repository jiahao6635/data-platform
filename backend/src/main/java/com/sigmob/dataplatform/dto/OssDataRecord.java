package com.sigmob.dataplatform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OssDataRecord(
        String bucket,
        String db,
        String table,
        String partition,
        @JsonProperty("size_bytes") Long sizeBytes,
        @JsonProperty("mod_time") String modTime,
        @JsonProperty("access_time") String accessTime,
        String owner,
        @JsonProperty("scan_type") String scanType,
        @JsonProperty("collect_host") String collectHost,
        @JsonProperty("collect_time") String collectTime
) {
}
