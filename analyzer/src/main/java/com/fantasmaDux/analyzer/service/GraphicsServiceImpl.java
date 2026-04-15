package com.fantasmaDux.analyzer.service;

import com.fantasmaDux.analyzer.dto.AnalysisResultDto;
import com.fantasmaDux.analyzer.dto.BranchStatisticsDto;
import com.fantasmaDux.analyzer.dto.ChartsDataDto;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GraphicsServiceImpl implements GraphicsService {

    @Override
    public void generateGraphics(AnalysisResultDto result) {
        // Подготавливаем данные для графиков
        ChartsDataDto chartsData = prepareChartsData(result);
        result.setChartsData(chartsData);
    }

    private ChartsDataDto prepareChartsData(AnalysisResultDto result) {
        Map<String, List<Double>> branchValues = new HashMap<>();
        Map<String, BranchStatisticsDto> branchStats = result.getBranchStatistics();

        // Собираем значения по филиалам
        for (Map.Entry<String, BranchStatisticsDto> entry : branchStats.entrySet()) {
            branchValues.put(entry.getKey(), entry.getValue().getValues());
        }

        // 1. Box plot данные
        Map<String, ChartsDataDto.BoxPlotData> boxPlotData = calculateBoxPlotData(branchValues);

        // 2. Bar chart данные (средние с доверительными интервалами)
        Map<String, Double> means = new HashMap<>();
        Map<String, Double> confidenceIntervals = new HashMap<>();

        for (Map.Entry<String, BranchStatisticsDto> entry : branchStats.entrySet()) {
            BranchStatisticsDto stats = entry.getValue();
            means.put(entry.getKey(), stats.getMean());
            double ci = 1.96 * stats.getStdDev() / Math.sqrt(stats.getCount());
            confidenceIntervals.put(entry.getKey(), ci);
        }

        // 3. Dot plot данные
        Map<String, List<Double>> allValues = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : branchValues.entrySet()) {
            allValues.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        return ChartsDataDto.builder()
                .boxPlotData(boxPlotData)
                .means(means)
                .confidenceIntervals(confidenceIntervals)
                .allValues(allValues)
                .build();
    }

    private Map<String, ChartsDataDto.BoxPlotData> calculateBoxPlotData(Map<String, List<Double>> branchValues) {
        Map<String, ChartsDataDto.BoxPlotData> result = new HashMap<>();

        for (Map.Entry<String, List<Double>> entry : branchValues.entrySet()) {
            String branch = entry.getKey();
            List<Double> values = new ArrayList<>(entry.getValue());
            values.sort(Double::compareTo);

            double min = values.get(0);
            double max = values.get(values.size() - 1);
            double q1 = percentile(values, 25);
            double median = percentile(values, 50);
            double q3 = percentile(values, 75);

            // Расчет выбросов
            double iqr = q3 - q1;
            double lowerBound = q1 - 1.5 * iqr;
            double upperBound = q3 + 1.5 * iqr;
            List<Double> outliers = new ArrayList<>();

            for (double v : values) {
                if (v < lowerBound || v > upperBound) {
                    outliers.add(v);
                }
            }

            result.put(branch, ChartsDataDto.BoxPlotData.builder()
                    .min(min)
                    .q1(q1)
                    .median(median)
                    .q3(q3)
                    .max(max)
                    .outliers(outliers)
                    .build());
        }

        return result;
    }

    private double percentile(List<Double> sorted, double percent) {
        int index = (int) Math.ceil(percent / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }
}