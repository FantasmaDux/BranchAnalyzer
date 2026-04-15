package com.fantasmaDux.analyzer.service;

import com.fantasmaDux.analyzer.dto.*;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class FileServiceImpl implements FileService {
    @Override
    public List<DataRowDto> parseFile(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new RuntimeException("Не удалось определить имя файла");
        }

        try {
            if (fileName.endsWith(".csv")) {
                return parseCsv(file);
            } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                return parseExcel(file);
            } else {
                throw new RuntimeException("Неподдерживаемый формат файла. Используйте .csv или .xlsx");
            }
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при парсинге файла: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] exportToPdf(AnalysisResultDto result) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4.rotate());
            document.setMargins(20, 20, 20, 20);

            // Заголовок
            addTitle(document, result);

            // 1. Исходные данные
            addRawDataTable(document, result.getRawData());

            // 2. Статистика по филиалам
            addBranchStatistics(document, result.getBranchStatistics());

            // 3. Тест гомогенности дисперсий
            addHomogeneityTest(document, result.getHomogeneityTest());

            // 4. ANOVA таблица
            addAnovaTable(document, result.getAnovaTable());

            // 5. Post-hoc тесты
            addPostHocTable(document, result.getPostHocTests());

            // 6. Ранжирование
            addRankingTable(document, result.getRanking());

            document.close();
            pdfDoc.close();

            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Ошибка при создании PDF: " + e.getMessage(), e);
        }
    }

    private void addTitle(Document document, AnalysisResultDto result) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));

        Paragraph title = new Paragraph("Анализ № " + date)
                .setFontSize(18)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(10);
        document.add(title);

        Paragraph subtitle = new Paragraph("Тип компании: " + result.getMetricType())
                .setFontSize(12)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20);
        document.add(subtitle);

        Paragraph info = new Paragraph(String.format(
                "Количество филиалов: %d | Всего сотрудников: %d",
                result.getBranchesCount(), result.getTotalEmployees()))
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(30);
        document.add(info);
    }

    private void addRawDataTable(Document document, List<DataRowDto> rawData) {
        Paragraph header = new Paragraph("1. Исходные данные")
                .setFontSize(14)
                .setBold()
                .setMarginTop(20)
                .setMarginBottom(10);
        document.add(header);

        Table table = new Table(UnitValue.createPercentArray(new float[]{40, 30, 30}));
        table.setWidth(UnitValue.createPercentValue(100));

        // Заголовки
        addCell(table, "Сотрудник", true);
        addCell(table, "Филиал", true);
        addCell(table, "Показатель", true);

        // Данные (только первые 20 строк)
        int rowCount = Math.min(rawData.size(), 20);
        for (int i = 0; i < rowCount; i++) {
            DataRowDto row = rawData.get(i);
            addCell(table, row.getEmployee(), false);
            addCell(table, row.getBranch(), false);
            addCell(table, String.valueOf(row.getValue()), false);
        }

        if (rawData.size() > 20) {
            Paragraph note = new Paragraph("... и еще " + (rawData.size() - 20) + " строк")
                    .setFontSize(9)
                    .setItalic();
            document.add(note);
        }

        document.add(table);
    }

    private void addBranchStatistics(Document document, java.util.Map<String, BranchStatisticsDto> stats) {
        Paragraph header = new Paragraph("2. Статистика по филиалам")
                .setFontSize(14)
                .setBold()
                .setMarginTop(20)
                .setMarginBottom(10);
        document.add(header);

        Table table = new Table(UnitValue.createPercentArray(new float[]{25, 15, 15, 15, 15, 15}));
        table.setWidth(UnitValue.createPercentValue(100));

        addCell(table, "Филиал", true);
        addCell(table, "Кол-во", true);
        addCell(table, "Среднее", true);
        addCell(table, "Дисперсия", true);
        addCell(table, "StdDev", true);
        addCell(table, "Min/Max", true);

        for (BranchStatisticsDto stat : stats.values()) {
            addCell(table, stat.getBranch(), false);
            addCell(table, String.valueOf(stat.getCount()), false);
            addCell(table, String.format("%.2f", stat.getMean()), false);
            addCell(table, String.format("%.2f", stat.getVariance()), false);
            addCell(table, String.format("%.2f", stat.getStdDev()), false);
            addCell(table, String.format("%.1f/%.1f", stat.getMin(), stat.getMax()), false);
        }

        document.add(table);
    }

    private void addHomogeneityTest(Document document, HomogeneityTestDto test) {
        Paragraph header = new Paragraph("3. Тест гомогенности дисперсий (Ливена)")
                .setFontSize(14)
                .setBold()
                .setMarginTop(20)
                .setMarginBottom(10);
        document.add(header);

        Table table = new Table(UnitValue.createPercentArray(new float[]{30, 30, 40}));
        table.setWidth(UnitValue.createPercentValue(100));

        addCell(table, "Статистика", true);
        addCell(table, "p-value", true);
        addCell(table, "Интерпретация", true);

        addCell(table, String.format("%.4f", test.getStatistic()), false);
        addCell(table, String.format("%.6f", test.getPValue()), false);
        addCell(table, test.isHomogeneous() ? "Дисперсии гомогенны" : "Дисперсии НЕ гомогенны", false);

        document.add(table);
    }

    private void addAnovaTable(Document document, AnovaTableDto anova) {
        Paragraph header = new Paragraph("4. ANOVA")
                .setFontSize(14)
                .setBold()
                .setMarginTop(20)
                .setMarginBottom(10);
        document.add(header);

        Table table = new Table(UnitValue.createPercentArray(new float[]{25, 15, 15, 15, 15, 15}));
        table.setWidth(UnitValue.createPercentValue(100));

        addCell(table, "Источник", true);
        addCell(table, "SS", true);
        addCell(table, "df", true);
        addCell(table, "MS", true);
        addCell(table, "F", true);
        addCell(table, "p-value", true);

        addCell(table, anova.getSource(), false);
        addCell(table, String.format("%.2f", anova.getSs()), false);
        addCell(table, String.valueOf(anova.getDf()), false);
        addCell(table, String.format("%.2f", anova.getMs()), false);
        addCell(table, String.format("%.4f", anova.getF()), false);
        addCell(table, String.format("%.6f", anova.getPValue()), false);

        document.add(table);
    }

    private void addPostHocTable(Document document, List<PostHocDto> postHocTests) {
        Paragraph header = new Paragraph("5. Post-hoc тесты (Tukey HSD)")
                .setFontSize(14)
                .setBold()
                .setMarginTop(20)
                .setMarginBottom(10);
        document.add(header);

        Table table = new Table(UnitValue.createPercentArray(new float[]{25, 15, 15, 15, 15, 15}));
        table.setWidth(UnitValue.createPercentValue(100));

        addCell(table, "Пары филиалов", true);
        addCell(table, "Разница средних", true);
        addCell(table, "SE", true);
        addCell(table, "p-value", true);
        addCell(table, "НЗР", true);
        addCell(table, "Значимо", true);

        for (PostHocDto postHoc : postHocTests) {
            addCell(table, postHoc.getPair(), false);
            addCell(table, String.format("%.2f", postHoc.getMeanDiff()), false);
            addCell(table, String.format("%.4f", postHoc.getSe()), false);
            addCell(table, String.format("%.6f", postHoc.getPValue()), false);
            addCell(table, String.format("%.2f", postHoc.getHsd()), false);
            addCell(table, postHoc.isSignificant() ? "Да" : "Нет", false);
        }

        document.add(table);
    }

    private void addRankingTable(Document document, List<RankingDto> ranking) {
        Paragraph header = new Paragraph("6. Ранжирование филиалов")
                .setFontSize(14)
                .setBold()
                .setMarginTop(20)
                .setMarginBottom(10);
        document.add(header);

        Table table = new Table(UnitValue.createPercentArray(new float[]{25, 50, 25}));
        table.setWidth(UnitValue.createPercentValue(100));

        addCell(table, "Ранг", true);
        addCell(table, "Филиал", true);
        addCell(table, "Среднее значение", true);

        for (int i = 0; i < ranking.size(); i++) {
            RankingDto rank = ranking.get(i);
            addCell(table, String.valueOf(i + 1), false);
            addCell(table, rank.getBranch(), false);
            addCell(table, String.format("%.2f", rank.getMean()), false);
        }

        document.add(table);
    }

    private void addCell(Table table, String text, boolean isHeader) {
        com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell().add(new Paragraph(text));
        if (isHeader) {
            cell.setBackgroundColor(ColorConstants.LIGHT_GRAY);
            cell.setBold();
        }
        cell.setPadding(5);
        table.addCell(cell);
    }

    private List<DataRowDto> parseExcel(MultipartFile file) throws IOException {
        List<DataRowDto> result = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                throw new RuntimeException("Файл не содержит заголовков");
            }

            // Определяем индексы колонок
            int employeeCol = -1;
            int branchCol = -1;
            int valueCol = -1;

            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                if (cell != null) {
                    String value = cell.getStringCellValue().toLowerCase();
                    if (matchesColumn(value, "employee", "сотрудник", "фио", "имя")) {
                        employeeCol = i;
                    } else if (matchesColumn(value, "branch", "филиал", "отдел", "подразделение")) {
                        branchCol = i;
                    } else if (matchesColumn(value, "value", "показатель", "значение", "результат")) {
                        valueCol = i;
                    }
                }
            }

            if (employeeCol == -1 || branchCol == -1 || valueCol == -1) {
                throw new RuntimeException("Не найдены необходимые колонки: сотрудник, филиал, показатель");
            }

            // Парсим данные
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    String employee = getCellValue(row.getCell(employeeCol));
                    String branch = getCellValue(row.getCell(branchCol));
                    double value = Double.parseDouble(getCellValue(row.getCell(valueCol)).replace(",", "."));

                    if (employee != null && !employee.isEmpty() && branch != null && !branch.isEmpty()) {
                        result.add(DataRowDto.builder()
                                .employee(employee)
                                .branch(branch)
                                .value(value)
                                .build());
                    }
                } catch (NumberFormatException e) {
                    // Пропускаем строки с некорректными числами
                }
            }
        }

        return result;
    }

    private List<DataRowDto> parseCsv(MultipartFile file) {
        List<DataRowDto> result = new ArrayList<>();

        try (InputStreamReader reader = new InputStreamReader(file.getInputStream())) {
            CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withIgnoreHeaderCase()
                    .withTrim());

            // Определяем названия колонок (могут быть на русском или английском)
            List<String> headers = csvParser.getHeaderNames();
            String employeeCol = findColumn(headers, "employee", "сотрудник", "фио", "имя");
            String branchCol = findColumn(headers, "branch", "филиал", "отдел", "подразделение");
            String valueCol = findColumn(headers, "value", "показатель", "значение", "результат");

            for (CSVRecord record : csvParser) {
                try {
                    DataRowDto dto = DataRowDto.builder()
                            .employee(record.get(employeeCol))
                            .branch(record.get(branchCol))
                            .value(Double.parseDouble(record.get(valueCol).replace(",", ".")))
                            .build();
                    result.add(dto);
                } catch (NumberFormatException e) {
                    // Пропускаем строки с некорректными числами
                    System.err.println("Пропущена строка с некорректным значением: " + record);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    private String findColumn(List<String> headers, String... possibleNames) {
        for (String header : headers) {
            String lowerHeader = header.toLowerCase();
            for (String name : possibleNames) {
                if (lowerHeader.equals(name) || lowerHeader.contains(name)) {
                    return header;
                }
            }
        }
        throw new RuntimeException("Не найдена колонка с данными. Ожидались: " + String.join(", ", possibleNames));
    }

    private boolean matchesColumn(String cellValue, String... possibleNames) {
        for (String name : possibleNames) {
            if (cellValue.equals(name) || cellValue.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }
}
