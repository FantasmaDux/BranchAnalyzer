package com.fantasmaDux.analyzer.service;

import com.fantasmaDux.analyzer.dto.AnalysisResultDto;
import com.fantasmaDux.analyzer.dto.DataRowDto;
import com.fantasmaDux.analyzer.enums.MetricTypeEnum;

import java.util.List;

public interface TablesService {
    public AnalysisResultDto calculateStatistics(List<DataRowDto> data, MetricTypeEnum metricType);
}
