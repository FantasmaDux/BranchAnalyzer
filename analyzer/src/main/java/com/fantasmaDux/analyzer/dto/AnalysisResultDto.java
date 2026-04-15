package com.fantasmaDux.analyzer.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AnalysisResultDto {
    // 1. Исходные данные
    private List<DataRowDto> rawData;

    // 2. Статистика по филиалам
    private Map<String, BranchStatisticsDto> branchStatistics;

    // 3. Тест гомогенности дисперсий (Ливен/Бартлетт)
    private HomogeneityTestDto homogeneityTest;

    // 4. Таблица ANOVA
    private AnovaTableDto anovaTable;

    // 5. Post-hoc тесты (Tukey HSD)
    private List<PostHocDto> postHocTests;

    // 6. Ранжирование филиалов
    private List<RankingDto> ranking;

    // 7. Данные для графиков
    private ChartsDataDto chartsData;

    // Метаданные анализа
    private String metricType;      // тип компании
    private String metricName;      // название метрики
    private int branchesCount;      // количество филиалов
    private int totalEmployees;     // всего сотрудников
}
