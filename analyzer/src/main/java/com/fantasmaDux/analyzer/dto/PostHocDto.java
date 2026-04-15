package com.fantasmaDux.analyzer.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PostHocDto {
    private String pair;         // "Филиал А vs Филиал Б"
    private double meanDiff;     // разница средних
    private double se;           // стандартная ошибка
    private double pValue;       // p-value
    private double hsd;          // НЗР (наименьшая значимая разница)
    private boolean significant; // значимо ли отличие
}
