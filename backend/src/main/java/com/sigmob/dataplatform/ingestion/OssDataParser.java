package com.sigmob.dataplatform.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigmob.dataplatform.dto.OssDataRecord;
import org.springframework.stereotype.Component;

@Component
public class OssDataParser {

    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> SUPPORTED_SCAN_TYPES = Set.of("table", "user", "tmp", "trash");

    private final ObjectMapper objectMapper;

    public OssDataParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedOssRecord parse(String json) {
        final OssDataRecord raw;
        try {
            raw = objectMapper.readValue(json, OssDataRecord.class);
        } catch (JsonProcessingException exception) {
            throw new InvalidOssDataException("不是合法的 JSON: " + exception.getOriginalMessage(), exception);
        }

        String bucket = required(raw.bucket(), "bucket");
        String database = normalized(raw.db());
        String table = normalized(raw.table());
        String partition = normalized(raw.partition());
        String owner = normalized(raw.owner());
        String scanType = required(raw.scanType(), "scan_type").toLowerCase(Locale.ROOT);
        String collectHost = normalized(raw.collectHost());

        if (!SUPPORTED_SCAN_TYPES.contains(scanType)) {
            throw new InvalidOssDataException("不支持的 scan_type: " + scanType);
        }
        if ("table".equals(scanType) && (database.isBlank() || table.isBlank())) {
            throw new InvalidOssDataException("table 类型必须同时提供 db 和 table");
        }
        if (raw.sizeBytes() == null || raw.sizeBytes() < 0) {
            throw new InvalidOssDataException("size_bytes 必须是大于等于 0 的整数");
        }

        LocalDateTime modTime = parseLocalTime(required(raw.modTime(), "mod_time"), "mod_time");
        LocalDateTime accessTime = raw.accessTime() == null || raw.accessTime().isBlank()
                ? null
                : parseLocalTime(raw.accessTime(), "access_time");
        OffsetDateTime collectTime = parseOffsetTime(required(raw.collectTime(), "collect_time"));

        String tableIdentity = "table".equals(scanType)
                ? String.join("\u001f", bucket, database, table)
                : String.join("\u001f", bucket, scanType, owner);
        String identity = partition.isBlank()
                ? tableIdentity
                : String.join("\u001f", tableIdentity, partition);
        String canonicalRow = String.join("\u001f",
                bucket,
                database,
                table,
                partition,
                Long.toString(raw.sizeBytes()),
                modTime.toString(),
                accessTime == null ? "" : accessTime.toString(),
                owner,
                scanType,
                collectHost,
                collectTime.toString());

        return new ParsedOssRecord(
                bucket,
                database,
                table,
                partition,
                raw.sizeBytes(),
                modTime,
                accessTime,
                owner,
                scanType,
                collectHost,
                collectTime,
                sha256(tableIdentity),
                sha256(identity),
                sha256(canonicalRow));
    }

    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    private LocalDateTime parseLocalTime(String value, String field) {
        try {
            return LocalDateTime.parse(value, LOCAL_TIME);
        } catch (DateTimeParseException exception) {
            throw new InvalidOssDataException(field + " 时间格式应为 yyyy-MM-dd HH:mm:ss: " + value, exception);
        }
    }

    private OffsetDateTime parseOffsetTime(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new InvalidOssDataException("collect_time 必须是带时区的 ISO-8601 时间: " + value, exception);
        }
    }

    private String required(String value, String field) {
        String normalized = normalized(value);
        if (normalized.isBlank()) {
            throw new InvalidOssDataException(field + " 不能为空");
        }
        return normalized;
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
