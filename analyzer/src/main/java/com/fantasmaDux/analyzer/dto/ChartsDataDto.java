package com.fantasmaDux.analyzer.dto;
import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ChartsDataDto {
    // Для box plot
    private Map<String, BoxPlotData> boxPlotData;

    // Для bar chart (средние с доверительными интервалами)
    private Map<String, Double> means;
    private Map<String, Double> confidenceIntervals;

    // Для dot plot
    private Map<String, List<Double>> allValues;

    @Data
    @Builder
    public static class BoxPlotData {
        private double min;
        private double q1;
        private double median;
        private double q3;
        private double max;
        private List<Double> outliers;
    }
}
