package com.sigmob.dataplatform.controller;

import java.util.List;

import com.sigmob.dataplatform.dto.ApiModels;
import com.sigmob.dataplatform.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard/summary")
    public ApiModels.Summary summary() {
        return dashboardService.summary();
    }

    @GetMapping("/dashboard/buckets")
    public List<ApiModels.BucketMetric> buckets() {
        return dashboardService.buckets();
    }

    @GetMapping("/dashboard/top-tables")
    public List<ApiModels.TopTable> topTables(@RequestParam(defaultValue = "10") int limit) {
        return dashboardService.topTables(limit);
    }

    @GetMapping("/dashboard/owners")
    public List<ApiModels.OwnerMetric> owners(@RequestParam(defaultValue = "8") int limit) {
        return dashboardService.owners(limit);
    }

    @GetMapping("/dashboard/trend")
    public List<ApiModels.TrendPoint> trend(
            @RequestParam(required = false) String bucket,
            @RequestParam(required = false) String database,
            @RequestParam(required = false) String table,
            @RequestParam(defaultValue = "30") int days
    ) {
        return dashboardService.trend(bucket, database, table, days);
    }

    @GetMapping("/assets")
    public ApiModels.Page<ApiModels.AssetItem> assets(
            @RequestParam(required = false) String bucket,
            @RequestParam(required = false) String database,
            @RequestParam(required = false) String scanType,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "sizeBytes") String sort,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return dashboardService.assets(
                bucket, database, scanType, owner, keyword, page, size, sort, direction);
    }

    @GetMapping("/assets/filters")
    public ApiModels.FilterOptions filters() {
        return dashboardService.filters();
    }

    @GetMapping("/partitions")
    public ApiModels.Page<ApiModels.PartitionItem> partitions(
            @RequestParam String bucket,
            @RequestParam String database,
            @RequestParam String table,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        return dashboardService.partitions(bucket, database, table, page, size);
    }

    @GetMapping("/snapshots")
    public List<ApiModels.SnapshotItem> snapshots(@RequestParam(defaultValue = "20") int limit) {
        return dashboardService.snapshots(limit);
    }
}
