package com.fantasmaDux.analyzer.controller;

import com.fantasmaDux.analyzer.dto.AnalysisResultDto;
import com.fantasmaDux.analyzer.dto.DataRowDto;
import com.fantasmaDux.analyzer.dto.StandardApiResponseDto;
import com.fantasmaDux.analyzer.enums.MetricTypeEnum;
import com.fantasmaDux.analyzer.service.FileService;
import com.fantasmaDux.analyzer.service.GraphicsService;
import com.fantasmaDux.analyzer.service.TablesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analyzer")
public class AnalyzerController {

    private final TablesService tablesService;
    private final GraphicsService graphicsService;
    private final FileService fileService;

    @PostMapping("/analyze")
    public ResponseEntity<StandardApiResponseDto<AnalysisResultDto>> analyze(
            @RequestParam("file") MultipartFile file,
            @RequestParam("metricType") String metricType
    ) {

        List<DataRowDto> data = fileService.parseFile(file);
        MetricTypeEnum metricTypeEnum = MetricTypeEnum.valueOf(metricType.toUpperCase());

        AnalysisResultDto result = tablesService.calculateStatistics(data, metricTypeEnum);

        graphicsService.generateGraphics(result);

        return ResponseEntity.ok(new StandardApiResponseDto<>("Tables analyzed successfully", result));
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestBody AnalysisResultDto result) {
        byte[] pdf = fileService.exportToPdf(result);

        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=analysis.pdf")
                .body(pdf);
    }
}
