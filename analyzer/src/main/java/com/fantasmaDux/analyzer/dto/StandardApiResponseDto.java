package com.fantasmaDux.analyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

// class for data returning to front
@Data
@AllArgsConstructor
public class StandardApiResponseDto<T> {
    private String message;
    private T data;
}