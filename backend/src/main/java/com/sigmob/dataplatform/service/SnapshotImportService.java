package com.sigmob.dataplatform.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.sigmob.dataplatform.dto.ApiModels;
import com.sigmob.dataplatform.ingestion.InvalidOssDataException;
import com.sigmob.dataplatform.ingestion.OssDataParser;
import com.sigmob.dataplatform.ingestion.ParsedOssRecord;
import com.sigmob.dataplatform.repository.SnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SnapshotImportService {

    private final OssDataParser parser;
    private final SnapshotRepository repository;

    public SnapshotImportService(OssDataParser parser, SnapshotRepository repository) {
        this.parser = parser;
        this.repository = repository;
    }

    @Transactional
    public ApiModels.ImportResult importNdjson(InputStream inputStream, String sourceName) throws IOException {
        List<ParsedOssRecord> records = new ArrayList<>();
        MessageDigest digest = newDigest();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                digest.update(line.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
                try {
                    records.add(parser.parse(line));
                } catch (InvalidOssDataException exception) {
                    throw new InvalidOssDataException("第 " + lineNumber + " 行解析失败: " + exception.getMessage(), exception);
                }
            }
        }

        if (records.isEmpty()) {
            throw new InvalidOssDataException("上传文件不包含任何 NDJSON 记录");
        }

        String checksum = HexFormat.of().formatHex(digest.digest());
        var existing = repository.findPublishedByChecksum(checksum);
        if (existing.isPresent()) {
            return existing.get().toImportResult(true);
        }

        UUID batchId = repository.createBatch("FILE", sourceName, checksum);
        for (ParsedOssRecord record : records) {
            repository.insertRecord(batchId, record);
        }
        repository.publishBatch(batchId);

        return repository.findBatch(batchId)
                .orElseThrow(() -> new IllegalStateException("导入完成后无法读取快照: " + batchId))
                .toImportResult(false);
    }

    private MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }
}

