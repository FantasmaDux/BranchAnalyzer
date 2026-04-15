package com.fantasmaDux.analyzer.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnovaTableDto {
    private String source;       // "Между группами" / "Внутри групп"
    private double ss;           // сумма квадратов
    private int df;              // степени свободы
    private double ms;           // средний квадрат
    private double f;            // F-статистика
    private double pValue;       // p-value
}
