package com.fantasmaDux.analyzer.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HomogeneityTestDto {
    private String testName;     // "Levene" или "Bartlett"
    private double statistic;    // статистика теста
    private double pValue;       // p-value
    private boolean homogeneous; // дисперсии гомогенны?
}
