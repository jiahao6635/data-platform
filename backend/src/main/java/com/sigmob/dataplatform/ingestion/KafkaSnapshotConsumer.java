package com.sigmob.dataplatform.ingestion;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.sigmob.dataplatform.config.AppProperties;
import com.sigmob.dataplatform.repository.SnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class KafkaSnapshotConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaSnapshotConsumer.class);

    private final Object batchLock = new Object();
    private final OssDataParser parser;
    private final SnapshotRepository repository;
    private final AppProperties properties;
    private final AtomicLong consumedRecordCount = new AtomicLong();

    private UUID activeBatchId;

    public KafkaSnapshotConsumer(
            OssDataParser parser,
            SnapshotRepository repository,
            AppProperties properties
    ) {
        this.parser = parser;
        this.repository = repository;
        this.properties = properties;
        log.info("Kafka 消费者已启动: topic={}", properties.kafka().topic());
    }

    @KafkaListener(topics = "${app.kafka.topic}")
    @Transactional
    public void consume(String payload) {
        final ParsedOssRecord record;
        try {
            record = parser.parse(payload);
        } catch (InvalidOssDataException exception) {
            repository.saveIngestionError("KAFKA", payload, exception.getMessage());
            log.warn("忽略无法解析的 OSS 元数据消息: {}", exception.getMessage());
            return;
        }

        synchronized (batchLock) {
            UUID batchId = activeBatch();
            repository.insertRecord(batchId, record);
        }
        consumedRecordCount.incrementAndGet();
    }

    @Scheduled(fixedDelayString = "${app.kafka.finalize-check-delay-ms:5000}")
    @Transactional
    public void finalizeQuietBatch() {
        synchronized (batchLock) {
            var batch = repository.findLatestReceivingKafkaBatch();
            if (batch.isEmpty()) {
                activeBatchId = null;
                return;
            }

            OffsetDateTime quietBefore = OffsetDateTime.now().minus(properties.kafka().quietPeriod());
            if (batch.get().lastReceivedAt().isAfter(quietBefore)) {
                activeBatchId = batch.get().id();
                log.info(
                        "Kafka 消费进行中: topic={}, batchId={}, 已处理消息数={}, 最近收到时间={}",
                        properties.kafka().topic(),
                        batch.get().id(),
                        consumedRecordCount.get(),
                        batch.get().lastReceivedAt());
                return;
            }

            repository.publishBatch(batch.get().id());
            log.info("Kafka 全量快照已发布: batchId={}", batch.get().id());
            activeBatchId = null;
        }
    }

    private UUID activeBatch() {
        if (activeBatchId != null) {
            var current = repository.findBatch(activeBatchId);
            if (current.isPresent() && "RECEIVING".equals(current.get().status())) {
                return activeBatchId;
            }
        }

        activeBatchId = repository.findLatestReceivingKafkaBatch()
                .map(SnapshotRepository.BatchState::id)
                .orElseGet(() -> repository.createBatch("KAFKA", properties.kafka().topic(), null));
        return activeBatchId;
    }
}
