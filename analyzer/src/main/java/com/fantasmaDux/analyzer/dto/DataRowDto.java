package com.fantasmaDux.analyzer.dto;

import lombok.*;


@Data
@Builder
public class DataRowDto {
    private String employee;      // сотрудник
    private String branch;        // филиал
    private double value;         // значение показателя
}
