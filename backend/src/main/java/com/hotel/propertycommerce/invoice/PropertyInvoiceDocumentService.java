package com.hotel.propertycommerce.invoice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Renders a deterministic, dependency-free PDF from an immutable invoice snapshot. */
@Service
public class PropertyInvoiceDocumentService {

    private static final int MAX_TEXT_WIDTH = 92;
    private static final int LINES_PER_PAGE = 43;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ObjectMapper objectMapper;

    public PropertyInvoiceDocumentService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] renderPdf(
            PropertyInvoice invoice,
            List<PropertyInvoiceLine> lines,
            List<PropertyInvoicePaymentAllocation> allocations,
            List<PropertyCreditNote> creditNotes,
            List<PropertyCreditNoteLine> creditNoteLines) {
        if (invoice == null || invoice.getStatus() != PropertyInvoice.Status.FINALIZED) {
            throw new IllegalArgumentException("Only finalized invoices can be rendered.");
        }

        return simplePdf(documentText(invoice, lines, allocations, creditNotes, creditNoteLines));
    }

    List<String> documentText(
            PropertyInvoice invoice,
            List<PropertyInvoiceLine> lines,
            List<PropertyInvoicePaymentAllocation> allocations,
            List<PropertyCreditNote> creditNotes,
            List<PropertyCreditNoteLine> creditNoteLines) {
        Map<String, Object> customer = snapshot(invoice.getCustomerSnapshotJson(), "customer");
        Map<String, Object> property = snapshot(invoice.getPropertySnapshotJson(), "property");
        List<String> text = new ArrayList<>();
        text.add("LUXESTAY - FINALIZED PROPERTY INVOICE");
        text.add("Invoice number: " + invoice.getInvoiceNumber());
        text.add("Finalized at UTC: " + formatDateTime(invoice.getFinalizedAt()));
        text.add("Reservation: " + invoice.getReservation().getId()
                + " | Status: " + invoice.getStatus().name()
                + " | Currency: " + invoice.getCurrency());
        text.add("");
        text.add("PROPERTY SNAPSHOT");
        text.add("Name: " + value(property, "nameVi", "nameEn", "name"));
        text.add("Code: " + value(property, "code") + " | Property ID: " + value(property, "hotelId"));
        text.add("Address: " + joinNonBlank(", ", value(property, "address"), value(property, "city"), value(property, "country")));
        text.add("Contact: " + joinNonBlank(" | ", value(property, "phone"), value(property, "email")));
        text.add("");
        text.add("CUSTOMER SNAPSHOT");
        text.add("Name: " + value(customer, "fullName", "username"));
        text.add("Customer ID: " + value(customer, "userId") + " | Username: " + value(customer, "username"));
        text.add("Contact: " + joinNonBlank(" | ", value(customer, "phone"), value(customer, "email")));
        text.add("");
        text.add("ITEMIZED LINES");

        List<PropertyInvoiceLine> safeLines = lines == null ? List.of() : lines;
        if (safeLines.isEmpty()) {
            text.add("No finalized invoice lines.");
        }
        for (int index = 0; index < safeLines.size(); index++) {
            PropertyInvoiceLine line = safeLines.get(index);
            text.add("Line " + (index + 1) + " [" + line.getLineType().name() + "]: " + line.getName());
            text.add("  Code: " + optional(line.getCode())
                    + " | Quantity: " + decimal(line.getQuantity())
                    + " | Unit price VND: " + decimal(line.getUnitPrice()));
            text.add("  Tax VND: " + decimal(line.getTaxAmount())
                    + " | Discount VND: " + decimal(line.getDiscountAmount())
                    + " | Total VND: " + decimal(line.getTotalAmount()));
            if (line.getDescription() != null && !line.getDescription().isBlank()) {
                text.add("  Description: " + line.getDescription().strip());
            }
            if (line.getUsageStartedAt() != null || line.getUsageEndedAt() != null) {
                text.add("  Usage: " + formatDateTime(line.getUsageStartedAt())
                        + " -> " + formatDateTime(line.getUsageEndedAt()));
            }
        }

        text.add("");
        text.add("PAYMENT ALLOCATIONS");
        List<PropertyInvoicePaymentAllocation> safeAllocations = allocations == null ? List.of() : allocations;
        if (safeAllocations.isEmpty()) {
            text.add("No allocated successful payments.");
        }
        for (int index = 0; index < safeAllocations.size(); index++) {
            PropertyInvoicePaymentAllocation allocation = safeAllocations.get(index);
            var transaction = allocation.getFinancialTransaction();
            text.add("Payment " + (index + 1)
                    + ": Amount VND: " + decimal(allocation.getAllocatedAmount())
                    + " | Method: " + optional(transaction.getMethod())
                    + " | Provider: " + optional(transaction.getProvider()));
            text.add("  Reference: " + optional(transaction.getPublicId())
                    + " | Occurred at UTC: " + formatDateTime(transaction.getOccurredAt()));
        }

        text.add("");
        text.add("REFUNDS AND CREDIT NOTES");
        text.add("Refunds/credits snapshot VND: " + decimal(invoice.getRefundedAmount()));
        List<PropertyCreditNote> safeNotes = creditNotes == null ? List.of() : creditNotes;
        if (safeNotes.isEmpty()) {
            text.add("No post-finalization credit notes.");
        }
        for (PropertyCreditNote note : safeNotes) {
            text.add("Credit note: " + note.getCreditNoteNumber()
                    + " | Amount VND: " + decimal(note.getAmount())
                    + " | Issued at UTC: " + formatDateTime(note.getIssuedAt()));
            text.add("  Reason: " + note.getReason());
            if (creditNoteLines != null) {
                creditNoteLines.stream()
                        .filter(line -> sameId(line.getCreditNote().getId(), note.getId()))
                        .forEach(line -> text.add("  Credit line: " + line.getDescription()
                                + " | Amount VND: " + decimal(line.getAmount())));
            }
        }

        text.add("");
        text.add("TOTALS");
        text.add("Subtotal VND: " + decimal(invoice.getSubtotal()));
        text.add("Tax VND: " + decimal(invoice.getTaxAmount()));
        text.add("Fees/surcharges VND: " + decimal(invoice.getFeeAmount()));
        text.add("Discount VND: " + decimal(invoice.getDiscountAmount()));
        text.add("TOTAL VND: " + decimal(invoice.getTotalAmount()));
        text.add("PAID VND: " + decimal(invoice.getPaidAmount()));
        text.add("REFUNDED/CREDITED VND: " + decimal(invoice.getRefundedAmount()));
        text.add("BALANCE VND: " + decimal(invoice.getBalanceAmount()));
        text.add("This document renders only the immutable finalized invoice snapshot.");
        return List.copyOf(text);
    }

    private byte[] simplePdf(List<String> lines) {
        List<String> wrapped = new ArrayList<>();
        for (String line : lines) {
            wrapped.addAll(wrap(toAscii(line), MAX_TEXT_WIDTH));
        }
        if (wrapped.isEmpty()) {
            wrapped.add("");
        }
        List<List<String>> pages = new ArrayList<>();
        for (int start = 0; start < wrapped.size(); start += LINES_PER_PAGE) {
            pages.add(List.copyOf(wrapped.subList(start, Math.min(start + LINES_PER_PAGE, wrapped.size()))));
        }

        int objectCount = 3 + (pages.size() * 2);
        int[] offsets = new int[objectCount + 1];
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            write(out, "%PDF-1.4\n%LUXE\n");
            offsets[1] = object(out, 1, "<< /Type /Catalog /Pages 2 0 R >>");

            StringBuilder kids = new StringBuilder();
            for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
                kids.append(4 + (pageIndex * 2)).append(" 0 R ");
            }
            offsets[2] = object(out, 2, "<< /Type /Pages /Kids [" + kids + "] /Count " + pages.size() + " >>");
            offsets[3] = object(out, 3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");

            for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
                int pageObjectId = 4 + (pageIndex * 2);
                int contentObjectId = pageObjectId + 1;
                offsets[pageObjectId] = object(out, pageObjectId,
                        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                                + "/Resources << /Font << /F1 3 0 R >> >> /Contents "
                                + contentObjectId + " 0 R >>");

                List<String> contentLines = new ArrayList<>();
                int y = 800;
                for (String line : pages.get(pageIndex)) {
                    contentLines.add("BT /F1 9 Tf 42 " + y + " Td (" + escape(line) + ") Tj ET");
                    y -= 17;
                }
                contentLines.add("BT /F1 8 Tf 500 24 Td (Page " + (pageIndex + 1)
                        + " of " + pages.size() + ") Tj ET");
                byte[] stream = String.join("\n", contentLines).getBytes(StandardCharsets.ISO_8859_1);
                offsets[contentObjectId] = out.size();
                write(out, contentObjectId + " 0 obj\n<< /Length " + stream.length + " >>\nstream\n");
                out.write(stream);
                write(out, "\nendstream\nendobj\n");
            }

            int xref = out.size();
            write(out, "xref\n0 " + (objectCount + 1) + "\n0000000000 65535 f \n");
            for (int objectId = 1; objectId <= objectCount; objectId++) {
                write(out, String.format("%010d 00000 n \n", offsets[objectId]));
            }
            write(out, "trailer\n<< /Size " + (objectCount + 1)
                    + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to render invoice PDF.", exception);
        }
    }

    private int object(ByteArrayOutputStream out, int objectId, String value) throws IOException {
        int offset = out.size();
        write(out, objectId + " 0 obj\n" + value + "\nendobj\n");
        return offset;
    }

    private void write(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private String toAscii(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private Map<String, Object> snapshot(String json, String label) {
        try {
            if (json == null || json.isBlank()) {
                throw new IllegalStateException("Finalized " + label + " snapshot is missing.");
            }
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (IOException exception) {
            throw new IllegalStateException("Finalized " + label + " snapshot is invalid.", exception);
        }
    }

    private String value(Map<String, Object> snapshot, String... keys) {
        for (String key : keys) {
            Object candidate = snapshot.get(key);
            if (candidate != null && !candidate.toString().isBlank()) {
                return candidate.toString().strip();
            }
        }
        return "-";
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? "-" : value.strip();
    }

    private String decimal(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "-" : DATE_TIME.format(value);
    }

    private String joinNonBlank(String separator, String... values) {
        List<String> present = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank() && !"-".equals(value)) {
                present.add(value);
            }
        }
        return present.isEmpty() ? "-" : String.join(separator, present);
    }

    private boolean sameId(Long left, Long right) {
        return left != null && left.equals(right);
    }

    private List<String> wrap(String value, int width) {
        if (value == null || value.isEmpty()) {
            return List.of("");
        }
        List<String> result = new ArrayList<>();
        String remaining = value;
        while (remaining.length() > width) {
            int split = remaining.lastIndexOf(' ', width);
            if (split <= 0) {
                split = width;
            }
            result.add(remaining.substring(0, split));
            remaining = remaining.substring(split).stripLeading();
        }
        result.add(remaining);
        return result;
    }
}
