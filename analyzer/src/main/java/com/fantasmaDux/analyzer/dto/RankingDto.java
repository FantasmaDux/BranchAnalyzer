package com.fantasmaDux.analyzer.dto;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RankingDto {
    private int rank;
    private String branch;
    private double mean;
}
