package com.fantasmaDux.analyzer.service;

import com.fantasmaDux.analyzer.dto.AnalysisResultDto;
import com.fantasmaDux.analyzer.dto.DataRowDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileService {
    List<DataRowDto> parseFile(MultipartFile file);
    byte[] exportToPdf(AnalysisResultDto result);

}
