package com.hotel.paymentprovider.reporting;

import com.hotel.paymentprovider.reporting.RevenueReportModels.ReconciliationIssue;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueReportResult;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueTransactionRow;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Renders every report format from the exact immutable report result supplied by the API. */
@Service
public class RevenueExportService {

    public enum Format {
        CSV("text/csv; charset=UTF-8", "csv"),
        EXCEL("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
        PDF("application/pdf", "pdf");

        private final String contentType;
        private final String extension;

        Format(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }
    }

    public ExportArtifact export(RevenueReportResult report, Format format) {
        Objects.requireNonNull(report, "report must not be null");
        Objects.requireNonNull(format, "format must not be null");
        String checksum = checksum(report);
        byte[] content = switch (format) {
            case CSV -> ("\uFEFF" + csv(report, checksum)).getBytes(StandardCharsets.UTF_8);
            case EXCEL -> excel(report, checksum);
            case PDF -> pdf(report, checksum);
        };
        return new ExportArtifact(
                format,
                content,
                format.contentType,
                "revenue-" + report.context().name().toLowerCase(java.util.Locale.ROOT) + "." + format.extension,
                checksum,
                report.totalRowCount());
    }

    public String checksum(RevenueReportResult report) {
        Objects.requireNonNull(report, "report must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical(report).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format(java.util.Locale.ROOT, "%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the runtime.", exception);
        }
    }

    private String csv(RevenueReportResult report, String checksum) {
        List<String> lines = new ArrayList<>();
        lines.add(row("BÁO CÁO DOANH THU LUXESTAY", report.context(), "Cơ sở ghi nhận", report.basis()));
        lines.add(row("Từ ngày", report.filters().fromInclusive(), "Đến ngày", report.filters().toExclusive(),
                "Múi giờ", report.filters().zoneId()));
        lines.add(row("Tổng thu", report.totals().grossRevenue(), "Hoàn tiền", report.totals().refunds(),
                "Điều chỉnh", report.totals().credits(), "Doanh thu ròng", report.totals().netRevenue()));
        lines.add(row("Giao dịch thành công", report.totals().successfulTransactionCount(),
                "Giao dịch thất bại", report.totals().failedTransactionCount(),
                "Chưa đối soát", report.totals().unreconciledTransactionCount(), "Mã kiểm tra", checksum));
        lines.add(row());
        lines.add(row("Mã giao dịch", "Thời gian", "Loại giao dịch", "Loại nguồn", "Mã nguồn", "Mã cơ sở",
                "Phương thức", "Nhà cung cấp", "Tổng tiền", "Hoàn tiền", "Điều chỉnh", "Doanh thu ròng",
                "Trạng thái đối soát", "Thông tin bổ sung"));
        report.rows().forEach(item -> lines.add(row(
                item.publicId(), item.occurredAt(), item.transactionType(), item.sourceType(), item.sourceId(),
                item.propertyId(), item.method(), item.provider(), item.grossAmount(), item.refundAmount(),
                item.creditAmount(), item.netAmount(), item.reconciliationStatus(), dimensions(item.dimensions()))));
        lines.add(row());
        lines.add(row("Mã sai lệch", "Loại nguồn", "Mã nguồn", "Số tiền dự kiến", "Số tiền thực tế",
                "Chênh lệch", "Nội dung"));
        report.reconciliationIssues().forEach(issue -> lines.add(row(
                issue.code(), issue.sourceType(), issue.sourceId(), issue.expectedAmount(), issue.actualAmount(),
                issue.deltaAmount(), issue.message())));
        return String.join("\n", lines) + "\n";
    }

    private byte[] excel(RevenueReportResult report, String checksum) {
        String csv = csv(report, checksum);
        List<String> rows = csv.lines().map(line -> parseCsv(line)).toList();
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                entry(zip, "[Content_Types].xml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                          <Default Extension="xml" ContentType="application/xml"/>
                          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                        </Types>
                        """);
                entry(zip, "_rels/.rels", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                        </Relationships>
                        """);
                entry(zip, "xl/workbook.xml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Revenue" sheetId="1" r:id="rId1"/></sheets></workbook>
                        """);
                entry(zip, "xl/_rels/workbook.xml.rels", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>
                        """);
                StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
                for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                    String[] cells = parseCsvValues(rows.get(rowIndex));
                    sheet.append("<row r=\"").append(rowIndex + 1).append("\">");
                    for (int column = 0; column < cells.length; column++) {
                        sheet.append("<c r=\"").append(cellRef(column, rowIndex + 1))
                                .append("\" t=\"inlineStr\"><is><t>")
                                .append(xml(cells[column])).append("</t></is></c>");
                    }
                    sheet.append("</row>");
                }
                sheet.append("</sheetData></worksheet>");
                entry(zip, "xl/worksheets/sheet1.xml", sheet.toString());
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to render Excel export.", exception);
        }
    }

    private byte[] pdf(RevenueReportResult report, String checksum) {
        List<String> text = new ArrayList<>();
        text.add("LuxeStay Revenue Report");
        text.add("Context: " + report.context());
        text.add("Basis: " + report.basis());
        text.add("Checksum: " + checksum);
        text.add("From: " + report.filters().fromInclusive());
        text.add("To: " + report.filters().toExclusive());
        text.add("Zone: " + report.filters().zoneId());
        text.add("Property: " + report.filters().propertyId());
        text.add("Provider: " + report.filters().provider());
        text.add("Method: " + report.filters().method());
        text.add("Transaction type: " + report.filters().transactionType());
        text.add("Room type: " + report.filters().roomType());
        text.add("Plan: " + report.filters().planCode());
        text.add("Gross VND: " + report.totals().grossRevenue());
        text.add("Refunds VND: " + report.totals().refunds());
        text.add("Credits VND: " + report.totals().credits());
        text.add("Net VND: " + report.totals().netRevenue());
        text.add("Cash collected VND: " + report.totals().cashCollected());
        text.add("Invoiced VND: " + report.totals().invoicedRevenue());
        text.add("Unpaid VND: " + report.totals().unpaidBalance());
        text.add("Held deposits VND: " + report.totals().heldDeposits());
        text.add("Successful transactions: " + report.totals().successfulTransactionCount());
        text.add("Failed transactions: " + report.totals().failedTransactionCount());
        text.add("Unreconciled transactions: " + report.totals().unreconciledTransactionCount());
        text.add("Rows: " + report.totalRowCount());
        report.rows().forEach(row -> text.add(row.publicId() + " | " + row.transactionType() + " | " + row.netAmount()));
        return simplePdf(text);
    }

    private byte[] simplePdf(List<String> lines) {
        int linesPerPage = 45;
        int pageCount = Math.max(1, (lines.size() + linesPerPage - 1) / linesPerPage);
        int fontObject = 3 + pageCount * 2;
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        String kids = java.util.stream.IntStream.range(0, pageCount)
                .mapToObj(index -> (3 + index * 2) + " 0 R")
                .reduce((left, right) -> left + " " + right).orElse("");
        objects.add("<< /Type /Pages /Kids [" + kids + "] /Count " + pageCount + " >>");
        for (int page = 0; page < pageCount; page++) {
            int contentObject = 4 + page * 2;
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 "
                    + fontObject + " 0 R >> >> /Contents " + contentObject + " 0 R >>");
            List<String> contentLines = new ArrayList<>();
            int start = page * linesPerPage;
            int end = Math.min(start + linesPerPage, lines.size());
            int y = 790;
            contentLines.add("BT /F1 8 Tf 500 815 Td (Page " + (page + 1) + "/" + pageCount + ") Tj ET");
            for (int index = start; index < end; index++) {
                contentLines.add("BT /F1 9 Tf 40 " + y + " Td (" + escape(ascii(lines.get(index))) + ") Tj ET");
                y -= 16;
            }
            byte[] stream = String.join("\n", contentLines).getBytes(StandardCharsets.ISO_8859_1);
            objects.add("<< /Length " + stream.length + " >>\nstream\n"
                    + new String(stream, StandardCharsets.ISO_8859_1) + "\nendstream");
        }
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            write(out, "%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n");
            List<Integer> offsets = new ArrayList<>();
            offsets.add(0);
            for (int index = 0; index < objects.size(); index++) {
                offsets.add(object(out, (index + 1) + " 0 obj\n" + objects.get(index) + "\nendobj\n"));
            }
            int xref = out.size();
            write(out, "xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
            for (int i = 1; i < offsets.size(); i++) {
                write(out, String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offsets.get(i)));
            }
            write(out, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to render PDF export.", exception);
        }
    }

    private String canonical(RevenueReportResult report) {
        StringBuilder value = new StringBuilder();
        value.append(report.context()).append('|').append(report.basis()).append('|')
                .append(report.filters().fromInclusive()).append('|').append(report.filters().toExclusive()).append('|')
                .append(report.filters().zoneId()).append('|').append(report.filters().propertyId()).append('|')
                .append(report.filters().provider()).append('|').append(report.filters().method()).append('|')
                .append(report.filters().transactionType()).append('|').append(report.filters().roomType()).append('|')
                .append(report.filters().planCode()).append('|').append(report.totals()).append('|');
        report.rows().forEach(row -> value.append(row.publicId()).append('|').append(row.occurredAt()).append('|')
                .append(row.transactionType()).append('|').append(row.sourceType()).append('|').append(row.sourceId()).append('|')
                .append(row.propertyId()).append('|').append(row.method()).append('|').append(row.provider()).append('|')
                .append(row.grossAmount()).append('|').append(row.refundAmount()).append('|').append(row.creditAmount()).append('|')
                .append(row.netAmount()).append('|').append(row.reconciliationStatus()).append('|').append(dimensions(row.dimensions())).append(';'));
        report.reconciliationIssues().forEach(issue -> value.append(issue.code()).append('|').append(issue.sourceType()).append('|')
                .append(issue.sourceId()).append('|').append(issue.expectedAmount()).append('|').append(issue.actualAmount()).append('|')
                .append(issue.deltaAmount()).append('|').append(issue.message()).append(';'));
        return value.toString();
    }

    private String row(Object... values) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) result.append(',');
            result.append(csvValue(values[index]));
        }
        return result.toString();
    }

    private String csvValue(Object value) {
        String normalized = value == null ? "" : value.toString();
        return '"' + normalized.replace("\"", "\"\"") + '"';
    }

    private String dimensions(Map<String, String> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) return "";
        return dimensions.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + '=' + entry.getValue()).reduce((left, right) -> left + ';' + right).orElse("");
    }

    private String parseCsv(String line) {
        return line;
    }

    private String[] parseCsvValues(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        values.add(value.toString());
        return values.toArray(String[]::new);
    }

    private String cellRef(int column, int row) {
        StringBuilder letters = new StringBuilder();
        int value = column + 1;
        while (value > 0) {
            int remainder = (value - 1) % 26;
            letters.insert(0, (char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return letters + Integer.toString(row);
    }

    private void entry(ZipOutputStream zip, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private String ascii(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("[^\\x20-\\x7E]", "?");
    }

    private int object(ByteArrayOutputStream out, String value) throws IOException {
        int offset = out.size();
        write(out, value);
        return offset;
    }

    private void write(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    public record ExportArtifact(
            Format format,
            byte[] content,
            String contentType,
            String fileName,
            String checksum,
            long rowCount) {

        public ExportArtifact {
            content = content == null ? new byte[0] : content.clone();
            if (rowCount < 0) throw new IllegalArgumentException("rowCount must not be negative.");
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
