package com.sigmob.dataplatform.ingestion;

import java.nio.file.Files;
import java.nio.file.Path;

import com.sigmob.dataplatform.config.AppProperties;
import com.sigmob.dataplatform.service.SnapshotImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredFileImporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfiguredFileImporter.class);

    private final AppProperties properties;
    private final SnapshotImportService importService;

    public ConfiguredFileImporter(AppProperties properties, SnapshotImportService importService) {
        this.properties = properties;
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (properties.importFile().isBlank()) {
            return;
        }

        Path path = Path.of(properties.importFile()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("APP_IMPORT_FILE 不存在或不是文件: " + path);
        }

        try (var input = Files.newInputStream(path)) {
            var result = importService.importNdjson(input, path.getFileName().toString());
            log.info("启动导入完成: batchId={}, records={}, duplicate={}",
                    result.batchId(), result.recordCount(), result.duplicate());
        }
    }
}

