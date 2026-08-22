package com.sigmob.dataplatform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OssDataParserTest {

    private OssDataParser parser;

    @BeforeEach
    void setUp() {
        parser = new OssDataParser(new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void parsesCurrentSampleShape() {
        var record = parser.parse("""
                {"bucket":"sig-warehouse","db":"dmp","table":"dmp_wide_table_for_dsp_online",\
                "size_bytes":7855590992,"mod_time":"2026-06-30 15:44:00","access_time":"",\
                "owner":"dataextract","scan_type":"table","collect_host":"master-1-1",\
                "collect_time":"2026-08-22T17:01:26+08:00"}
                """);

        assertThat(record.bucket()).isEqualTo("sig-warehouse");
        assertThat(record.database()).isEqualTo("dmp");
        assertThat(record.table()).isEqualTo("dmp_wide_table_for_dsp_online");
        assertThat(record.partition()).isEmpty();
        assertThat(record.sizeBytes()).isEqualTo(7_855_590_992L);
        assertThat(record.accessTime()).isNull();
        assertThat(record.assetKey()).hasSize(64);
        assertThat(record.rowHash()).hasSize(64);
    }

    @Test
    void includesPartitionInAssetIdentityButNotTableIdentity() {
        var first = parser.parse("""
                {"bucket":"sigbakup-osshdfs","db":"dsp_log","table":"ad_material_log_compress",\
                "partition":"ds=2025-08-19","size_bytes":100,"mod_time":"2025-08-20 00:23:00",\
                "access_time":"","owner":"flink","scan_type":"table","collect_host":"master-1-1",\
                "collect_time":"2026-08-22T17:47:43+08:00"}
                """);
        var second = parser.parse("""
                {"bucket":"sigbakup-osshdfs","db":"dsp_log","table":"ad_material_log_compress",\
                "partition":"ds=2025-08-20","size_bytes":120,"mod_time":"2025-08-21 00:24:00",\
                "access_time":"","owner":"flink","scan_type":"table","collect_host":"master-1-1",\
                "collect_time":"2026-08-22T17:47:43+08:00"}
                """);

        assertThat(first.partition()).isEqualTo("ds=2025-08-19");
        assertThat(first.tableKey()).isEqualTo(second.tableKey());
        assertThat(first.assetKey()).isNotEqualTo(second.assetKey());
    }

    @Test
    void rejectsTableWithoutDatabaseAndName() {
        assertThatThrownBy(() -> parser.parse("""
                {"bucket":"sig-warehouse","db":"","table":"","size_bytes":0,\
                "mod_time":"2026-06-30 15:44:00","access_time":"","owner":"dataextract",\
                "scan_type":"table","collect_host":"master-1-1",\
                "collect_time":"2026-08-22T17:01:26+08:00"}
                """))
                .isInstanceOf(InvalidOssDataException.class)
                .hasMessageContaining("db 和 table");
    }
}
