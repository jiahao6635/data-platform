package com.sigmob.dataplatform.service;

import java.util.List;

import com.sigmob.dataplatform.dto.ApiModels;
import com.sigmob.dataplatform.repository.SnapshotRepository;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final SnapshotRepository repository;

    public DashboardService(SnapshotRepository repository) {
        this.repository = repository;
    }

    public ApiModels.Summary summary() {
        return repository.loadSummary();
    }

    public List<ApiModels.BucketMetric> buckets() {
        return repository.loadBucketMetrics();
    }

    public List<ApiModels.TopTable> topTables(int limit) {
        return repository.loadTopTables(boundedLimit(limit));
    }

    public List<ApiModels.OwnerMetric> owners(int limit) {
        return repository.loadOwnerMetrics(boundedLimit(limit));
    }

    public List<ApiModels.TrendPoint> trend(String bucket, String database, String table, int days) {
        int boundedDays = Math.max(1, Math.min(days, 3650));
        return repository.loadTrend(bucket, database, table, boundedDays);
    }

    public ApiModels.Page<ApiModels.AssetItem> assets(
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
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(size, 200));
        return repository.loadAssets(
                bucket,
                database,
                scanType,
                owner,
                keyword,
                boundedPage,
                boundedSize,
                sort,
                direction);
    }

    public ApiModels.FilterOptions filters() {
        return repository.loadFilterOptions();
    }

    public ApiModels.Page<ApiModels.PartitionItem> partitions(
            String bucket,
            String database,
            String table,
            int page,
            int size
    ) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(size, 500));
        return repository.loadPartitions(bucket, database, table, boundedPage, boundedSize);
    }

    public List<ApiModels.SnapshotItem> snapshots(int limit) {
        return repository.loadSnapshots(boundedLimit(limit));
    }

    private int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }
}
