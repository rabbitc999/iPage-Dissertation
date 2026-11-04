package com.rabbitc.ims.service;

import com.rabbitc.ims.web.ApiException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Keeps file encoding separate from CRUD authorization and validation. */
public enum TabularFiles {
    CSV, XLSX;

    public static final int MAX_ROWS = 5000;

    public static TabularFiles parse(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "csv" -> CSV;
            case "xlsx" -> XLSX;
            default -> throw ApiException.badRequest("请选择 CSV 或 Excel (.xlsx) 文件");
        };
    }

    public byte[] write(List<String> headers, List<List<String>> rows) throws IOException {
        if (this == CSV) {
            StringWriter output = new StringWriter();
            output.write('\ufeff');
            try (CSVPrinter printer = new CSVPrinter(output, CSVFormat.DEFAULT)) {
                printer.printRecord(headers);
                for (List<String> row : rows) {
                    printer.printRecord(row.stream().map(TabularFiles::safeCsv).toList());
                }
            }
            return output.toString().getBytes(StandardCharsets.UTF_8);
        }
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Data");
            writeRow(sheet.createRow(0), headers);
            for (int index = 0; index < rows.size(); index++) {
                writeRow(sheet.createRow(index + 1), rows.get(index));
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static String safeCsv(String value) {
        String trimmed = value.stripLeading();
        if ((!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0
                && !trimmed.matches("-[0-9]+(?:\\.[0-9]+)?"))
                || value.startsWith("\t") || value.startsWith("\r") || value.startsWith("\n")) {
            return "'" + value;
        }
        return value;
    }

    private static void writeRow(Row row, List<String> values) {
        for (int column = 0; column < values.size(); column++) {
            row.createCell(column, CellType.STRING).setCellValue(values.get(column));
        }
    }

    public List<List<String>> read(InputStream input) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        if (this == CSV) {
            try (PushbackReader reader = new PushbackReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                int first = reader.read();
                if (first != -1 && first != '\ufeff') reader.unread(first);
                try (var parser = CSVFormat.DEFAULT.parse(reader)) {
                    for (var record : parser) {
                        checkLimit(rows.size());
                        List<String> values = new ArrayList<>();
                        record.forEach(values::add);
                        rows.add(values);
                    }
                }
            }
        } else {
            try (Workbook workbook = new XSSFWorkbook(input)) {
                if (workbook.getNumberOfSheets() == 0) return rows;
                DataFormatter formatter = new DataFormatter(Locale.ROOT);
                for (Row row : workbook.getSheetAt(0)) {
                    checkLimit(rows.size());
                    List<String> values = new ArrayList<>();
                    for (int column = 0; column < row.getLastCellNum(); column++) {
                        Cell cell = row.getCell(column);
                        if (cell != null && (cell.getCellType() == CellType.FORMULA
                                || cell.getCellType() == CellType.ERROR)) {
                            throw ApiException.badRequest("Excel 第 " + (row.getRowNum() + 1) + " 行包含公式或错误值");
                        }
                        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                            values.add(cell.getLocalDateTimeCellValue().toString());
                        } else {
                            values.add(formatter.formatCellValue(cell));
                        }
                    }
                    rows.add(values);
                }
            }
        }
        return rows;
    }

    private static void checkLimit(int size) {
        if (size > MAX_ROWS) throw ApiException.badRequest("一次最多导入 " + MAX_ROWS + " 条记录");
    }
}