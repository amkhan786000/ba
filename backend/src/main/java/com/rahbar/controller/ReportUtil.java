package com.rahbar.controller;

import org.apache.commons.csv.CSVFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shared CSV/Excel export helpers used by the *_generate_reports endpoints. */
final class ReportUtil {
    private ReportUtil() {}

    static byte[] toCsv(List<Map<String, Object>> data) throws IOException {
        var out = new ByteArrayOutputStream();
        List<String> columns = new ArrayList<>(data.get(0).keySet());
        try (var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             var printer = new org.apache.commons.csv.CSVPrinter(writer,
                     CSVFormat.DEFAULT.builder().setHeader(columns.toArray(new String[0])).build())) {
            for (Map<String, Object> row : data) {
                List<Object> values = new ArrayList<>();
                for (String c : columns) values.add(row.get(c));
                printer.printRecord(values);
            }
        }
        return out.toByteArray();
    }

    static byte[] toExcel(List<Map<String, Object>> data) throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Report");
            List<String> columns = new ArrayList<>(data.get(0).keySet());
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) header.createCell(i).setCellValue(columns.get(i));
            int rowIdx = 1;
            for (Map<String, Object> row : data) {
                Row r = sheet.createRow(rowIdx++);
                for (int i = 0; i < columns.size(); i++) {
                    Object v = row.get(columns.get(i));
                    r.createCell(i).setCellValue(v == null ? "" : v.toString());
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    static byte[] toPdf(List<Map<String, Object>> data, String title) throws IOException {
        try {
            return buildPdf(data, title);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static byte[] buildPdf(List<Map<String, Object>> data, String title) throws Exception {
        var out = new ByteArrayOutputStream();
        var doc = new com.lowagie.text.Document(com.lowagie.text.PageSize.A4.rotate(), 20, 20, 20, 20);
        com.lowagie.text.pdf.PdfWriter.getInstance(doc, out);
        doc.open();
        var titleFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 14);
        var headFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 8);
        var cellFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA, 7);
        doc.add(new com.lowagie.text.Paragraph(title, titleFont));
        doc.add(new com.lowagie.text.Paragraph(" "));
        List<String> columns = new ArrayList<>(data.get(0).keySet());
        var table = new com.lowagie.text.pdf.PdfPTable(columns.size());
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String c : columns) {
            var cell = new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase(c, headFont));
            cell.setBackgroundColor(new java.awt.Color(0xF2, 0xF2, 0xF2));
            table.addCell(cell);
        }
        for (Map<String, Object> row : data) {
            for (String c : columns) {
                Object v = row.get(c);
                table.addCell(new com.lowagie.text.Phrase(v == null ? "" : v.toString(), cellFont));
            }
        }
        doc.add(table);
        doc.close();
        return out.toByteArray();
    }
}
