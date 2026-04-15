package com.fantasmaDux.analyzer.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MetricTypeEnum {

    MARKETING("Торговля"),
    PRODUCTION("Производство"),
    TECHNICAL_SUPPORT("Техническая поддержка");

    private final String value  ;
}
