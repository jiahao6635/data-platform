package com.sigmob.dataplatform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import com.sigmob.dataplatform.repository.SnapshotRepository;
import com.sigmob.dataplatform.service.SnapshotImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = "app.kafka.enabled=false")
class SnapshotImportIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    SnapshotImportService importService;

    @Autowired
    SnapshotRepository repository;

    @Test
    void importsSnapshotAndMakesRetryIdempotent() throws Exception {
        String ndjson = """
                {"bucket":"bucket-a","db":"dmp","table":"table_a","partition":"ds=2026-08-20","size_bytes":1000,"mod_time":"2026-08-20 10:00:00","access_time":"","owner":"alice","scan_type":"table","collect_host":"master-1","collect_time":"2026-08-22T17:01:26+08:00"}
                {"bucket":"bucket-a","db":"dmp","table":"table_a","partition":"ds=2026-08-21","size_bytes":1500,"mod_time":"2026-08-21 10:00:00","access_time":"","owner":"alice","scan_type":"table","collect_host":"master-1","collect_time":"2026-08-22T17:12:36+08:00"}
                {"bucket":"bucket-a","db":"dmp","table":"table_b","size_bytes":2500,"mod_time":"2026-08-21 10:00:00","access_time":"","owner":"bob","scan_type":"table","collect_host":"master-1","collect_time":"2026-08-22T17:12:36+08:00"}
                {"bucket":"bucket-a","db":"","table":"","size_bytes":400,"mod_time":"2026-08-22 10:00:00","access_time":"","owner":"alice","scan_type":"tmp","collect_host":"master-1","collect_time":"2026-08-22T17:12:36+08:00"}
                """;

        var first = importService.importNdjson(stream(ndjson), "sample.ndjson");
        var retry = importService.importNdjson(stream(ndjson), "sample.ndjson");
        var summary = repository.loadSummary();

        assertThat(first.duplicate()).isFalse();
        assertThat(first.recordCount()).isEqualTo(4);
        assertThat(first.tableCount()).isEqualTo(2);
        assertThat(first.partitionCount()).isEqualTo(2);
        assertThat(first.totalTableSizeBytes()).isEqualTo(5000);
        assertThat(retry.duplicate()).isTrue();
        assertThat(retry.batchId()).isEqualTo(first.batchId());
        assertThat(summary.totalSizeBytes()).isEqualTo(5000);
        assertThat(summary.tableCount()).isEqualTo(2);
        assertThat(summary.partitionCount()).isEqualTo(2);
        assertThat(summary.partitionedTableCount()).isEqualTo(1);
        assertThat(summary.rawRecordCount()).isEqualTo(4);
    }

    private ByteArrayInputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
