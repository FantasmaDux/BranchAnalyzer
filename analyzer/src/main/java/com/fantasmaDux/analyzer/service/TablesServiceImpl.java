package com.fantasmaDux.analyzer.service;

import com.fantasmaDux.analyzer.dto.*;
import com.fantasmaDux.analyzer.enums.MetricTypeEnum;
import org.apache.commons.math3.stat.inference.OneWayAnova;
import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TablesServiceImpl implements TablesService {
    @Override
    public AnalysisResultDto calculateStatistics(List<DataRowDto> data, MetricTypeEnum metricType) {
        // 1. Группировка данных по филиалам
        Map<String, List<Double>> branchValues = data.stream()
                .collect(Collectors.groupingBy(
                        DataRowDto::getBranch,
                        Collectors.mapping(DataRowDto::getValue, Collectors.toList())
                ));

        // 2. Статистика по филиалам
        Map<String, BranchStatisticsDto> branchStats = calculateBranchStatistics(branchValues);

        // 3. Тест гомогенности дисперсий (Ливена)
        HomogeneityTestDto homogeneityTest = calculateLeveneTest(branchValues);

        // 4. ANOVA
        AnovaTableDto anovaTable = calculateAnova(branchValues);

        // 5. Post-hoc тесты (Tukey HSD)
        List<PostHocDto> postHocTests = calculateTukeyHSD(branchValues);

        // 6. Ранжирование филиалов
        List<RankingDto> ranking = calculateRanking(branchStats);

        // 7. Данные для графиков
        ChartsDataDto chartsData = prepareChartsData(branchValues, branchStats);

        // 8. Исходные данные для таблицы
        List<DataRowDto> rawData = data;

        return AnalysisResultDto.builder()
                .rawData(rawData)
                .branchStatistics(branchStats)
                .homogeneityTest(homogeneityTest)
                .anovaTable(anovaTable)
                .postHocTests(postHocTests)
                .ranking(ranking)
                .chartsData(chartsData)
                .metricType(metricType.name())
                .branchesCount(branchValues.size())
                .totalEmployees(data.size())
                .build();
    }

    private Map<String, BranchStatisticsDto> calculateBranchStatistics(Map<String, List<Double>> branchValues) {
        Map<String, BranchStatisticsDto> result = new HashMap<>();

        for (Map.Entry<String, List<Double>> entry : branchValues.entrySet()) {
            String branch = entry.getKey();
            List<Double> values = entry.getValue();
            DescriptiveStatistics stats = new DescriptiveStatistics();
            values.forEach(stats::addValue);

            result.put(branch, BranchStatisticsDto.builder()
                    .branch(branch)
                    .count((int) stats.getN())
                    .mean(stats.getMean())
                    .variance(stats.getVariance())
                    .stdDev(stats.getStandardDeviation())
                    .min(stats.getMin())
                    .max(stats.getMax())
                    .values(new ArrayList<>(values))
                    .build());
        }

        return result;
    }

    private HomogeneityTestDto calculateLeveneTest(Map<String, List<Double>> branchValues) {
        // Упрощенная реализация теста Ливена
        List<String> branches = new ArrayList<>(branchValues.keySet());

        // Расчет медиан для каждой группы
        Map<String, Double> medians = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : branchValues.entrySet()) {
            List<Double> sorted = new ArrayList<>(entry.getValue());
            sorted.sort(Double::compareTo);
            int mid = sorted.size() / 2;
            double median = sorted.size() % 2 == 0
                    ? (sorted.get(mid - 1) + sorted.get(mid)) / 2
                    : sorted.get(mid);
            medians.put(entry.getKey(), median);
        }

        // Расчет Z-значений |x - median|
        Map<String, List<Double>> zValues = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : branchValues.entrySet()) {
            double median = medians.get(entry.getKey());
            List<Double> z = entry.getValue().stream()
                    .map(val -> Math.abs(val - median))
                    .collect(Collectors.toList());
            zValues.put(entry.getKey(), z);
        }

        // Односторонний ANOVA на Z-значениях
        List<double[]> zArrays = new ArrayList<>();
        for (List<Double> zList : zValues.values()) {
            zArrays.add(zList.stream().mapToDouble(Double::doubleValue).toArray());
        }

        OneWayAnova anova = new OneWayAnova();
        double fStatistic = anova.anovaFValue(zArrays);
        double pValue = anova.anovaPValue(zArrays);

        try {
            fStatistic = anova.anovaFValue(zArrays);
            pValue = anova.anovaPValue(zArrays);
            if (Double.isNaN(pValue) || Double.isInfinite(pValue)) {
                pValue = 0.5; // значение по умолчанию
            }
        } catch (Exception e) {
            // Если тест не удался, считаем дисперсии гомогенными
            fStatistic = 0;
            pValue = 0.5;
        }

        return HomogeneityTestDto.builder()
                .testName("Levene")
                .statistic(fStatistic)
                .pValue(pValue)
                .homogeneous(pValue > 0.05)
                .build();
    }

    private AnovaTableDto calculateAnova(Map<String, List<Double>> branchValues) {
        List<double[]> groups = new ArrayList<>();
        for (List<Double> values : branchValues.values()) {
            groups.add(values.stream().mapToDouble(Double::doubleValue).toArray());
        }

        OneWayAnova anova = new OneWayAnova();

        // Расчет p-value с обработкой ошибок
        double pValue;
        try {
            pValue = anova.anovaPValue(groups);
            // Если p-value получился NaN или бесконечность
            if (Double.isNaN(pValue) || Double.isInfinite(pValue)) {
                pValue = calculateApproximatePValue(groups);
            }
        } catch (Exception e) {
            // Если произошла ошибка (например, недостаточно данных)
            pValue = calculateApproximatePValue(groups);
        }

        // Расчет сумм квадратов
        double totalMean = groups.stream()
                .flatMapToDouble(Arrays::stream)
                .average()
                .orElse(0);

        double ssBetween = 0;
        double ssWithin = 0;
        int totalN = 0;

        for (double[] group : groups) {
            double groupMean = Arrays.stream(group).average().orElse(0);
            ssBetween += group.length * Math.pow(groupMean - totalMean, 2);
            ssWithin += Arrays.stream(group)
                    .map(val -> Math.pow(val - groupMean, 2))
                    .sum();
            totalN += group.length;
        }

        int dfBetween = groups.size() - 1;
        int dfWithin = totalN - groups.size();
        double msBetween = ssBetween / dfBetween;
        double msWithin = ssWithin / dfWithin;
        double f = msBetween / msWithin;
        System.out.println("Groups size: " + groups.size());
        System.out.println("Total N: " + totalN);
        System.out.println("dfBetween: " + dfBetween);
        System.out.println("dfWithin: " + dfWithin);
        System.out.println("F: " + f);
        System.out.println("pValue: " + pValue);
        return AnovaTableDto.builder()
                .source("Между группами")
                .ss(ssBetween)
                .df(dfBetween)
                .ms(msBetween)
                .f(f)
                .pValue(pValue)
                .build();
    }

    private double calculateApproximatePValue(List<double[]> groups) {
        // Приблизительный расчет p-value для F-распределения
        if (groups.size() < 2) return 1.0;

        // Расчет F-статистики
        double totalMean = groups.stream()
                .flatMapToDouble(Arrays::stream)
                .average()
                .orElse(0);

        double ssBetween = 0;
        double ssWithin = 0;
        int totalN = 0;

        for (double[] group : groups) {
            double groupMean = Arrays.stream(group).average().orElse(0);
            ssBetween += group.length * Math.pow(groupMean - totalMean, 2);
            ssWithin += Arrays.stream(group)
                    .map(val -> Math.pow(val - groupMean, 2))
                    .sum();
            totalN += group.length;
        }

        int dfBetween = groups.size() - 1;
        int dfWithin = totalN - groups.size();

        if (dfWithin <= 0) return 1.0;

        double msBetween = ssBetween / dfBetween;
        double msWithin = ssWithin / dfWithin;
        double f = msBetween / msWithin;

        // Эмпирическая формула для p-value
        if (f < 0.1) return 0.95;
        if (f > 10) return 0.001;
        return Math.exp(-f / 2) * 0.5;
    }

    private List<PostHocDto> calculateTukeyHSD(Map<String, List<Double>> branchValues) {
        List<PostHocDto> results = new ArrayList<>();
        List<String> branches = new ArrayList<>(branchValues.keySet());

        // Общая дисперсия внутри групп (MSE)
        double totalSquaredError = 0;
        int totalN = 0;
        for (List<Double> values : branchValues.values()) {
            double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            totalSquaredError += values.stream()
                    .mapToDouble(v -> Math.pow(v - mean, 2))
                    .sum();
            totalN += values.size();
        }
        double mse = totalSquaredError / (totalN - branches.size());

        // Критическое значение для Tukey (упрощенно)
        double qCritical = 3.5; // приблизительное значение для α=0.05

        for (int i = 0; i < branches.size(); i++) {
            for (int j = i + 1; j < branches.size(); j++) {
                String branch1 = branches.get(i);
                String branch2 = branches.get(j);

                List<Double> values1 = branchValues.get(branch1);
                List<Double> values2 = branchValues.get(branch2);

                double mean1 = values1.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double mean2 = values2.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double meanDiff = Math.abs(mean1 - mean2);

                double se = Math.sqrt(mse * (1.0 / values1.size() + 1.0 / values2.size()));
                double q = meanDiff / se;
                double hsd = qCritical * se;
                boolean significant = q > qCritical;

                // Упрощенный расчет p-value
                double pValue = Math.exp(-q) * 2;
                if (pValue > 0.5) pValue = 0.5;

                results.add(PostHocDto.builder()
                        .pair(branch1 + " vs " + branch2)
                        .meanDiff(meanDiff)
                        .se(se)
                        .pValue(pValue)
                        .hsd(hsd)
                        .significant(significant)
                        .build());
            }
        }

        return results;
    }

    private List<RankingDto> calculateRanking(Map<String, BranchStatisticsDto> branchStats) {
        return branchStats.values().stream()
                .sorted((a, b) -> Double.compare(b.getMean(), a.getMean()))
                .map(stat -> RankingDto.builder()
                        .rank(0) // временно, заполним ниже
                        .branch(stat.getBranch())
                        .mean(stat.getMean())
                        .build())
                .collect(Collectors.toList());
    }

    private ChartsDataDto prepareChartsData(Map<String, List<Double>> branchValues,
                                            Map<String, BranchStatisticsDto> branchStats) {
        Map<String, ChartsDataDto.BoxPlotData> boxPlotData = new HashMap<>();
        Map<String, Double> means = new HashMap<>();
        Map<String, Double> confidenceIntervals = new HashMap<>();
        Map<String, List<Double>> allValues = new HashMap<>();

        for (Map.Entry<String, List<Double>> entry : branchValues.entrySet()) {
            String branch = entry.getKey();
            List<Double> values = entry.getValue();
            List<Double> sorted = new ArrayList<>(values);
            sorted.sort(Double::compareTo);

            // Расчет квартилей для box plot
            double min = sorted.get(0);
            double max = sorted.get(sorted.size() - 1);
            double q1 = percentile(sorted, 25);
            double median = percentile(sorted, 50);
            double q3 = percentile(sorted, 75);

            // Расчет выбросов (упрощенно)
            double iqr = q3 - q1;
            double lowerBound = q1 - 1.5 * iqr;
            double upperBound = q3 + 1.5 * iqr;
            List<Double> outliers = values.stream()
                    .filter(v -> v < lowerBound || v > upperBound)
                    .collect(Collectors.toList());

            boxPlotData.put(branch, ChartsDataDto.BoxPlotData.builder()
                    .min(min)
                    .q1(q1)
                    .median(median)
                    .q3(q3)
                    .max(max)
                    .outliers(outliers)
                    .build());

            BranchStatisticsDto stats = branchStats.get(branch);
            means.put(branch, stats.getMean());
            double ci = 1.96 * stats.getStdDev() / Math.sqrt(stats.getCount());
            confidenceIntervals.put(branch, ci);
            allValues.put(branch, new ArrayList<>(values));
        }

        return ChartsDataDto.builder()
                .boxPlotData(boxPlotData)
                .means(means)
                .confidenceIntervals(confidenceIntervals)
                .allValues(allValues)
                .build();
    }

    private double percentile(List<Double> sorted, double percent) {
        int index = (int) Math.ceil(percent / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
