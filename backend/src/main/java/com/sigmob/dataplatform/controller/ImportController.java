package com.sigmob.dataplatform.controller;

import java.io.IOException;

import com.sigmob.dataplatform.dto.ApiModels;
import com.sigmob.dataplatform.service.SnapshotImportService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final SnapshotImportService importService;

    public ImportController(SnapshotImportService importService) {
        this.importService = importService;
    }

    @PostMapping(value = "/ndjson", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiModels.ImportResult importNdjson(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件为空");
        }
        return importService.importNdjson(file.getInputStream(), file.getOriginalFilename());
    }
}

