package com.fantasmaDux.analyzer.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BranchStatisticsDto {
    private String branch;
    private int count;           // количество сотрудников
    private double mean;         // среднее
    private double variance;     // дисперсия
    private double stdDev;       // стандартное отклонение
    private double min;          // минимум
    private double max;          // максимум
    private List<Double> values; // все значения
}
