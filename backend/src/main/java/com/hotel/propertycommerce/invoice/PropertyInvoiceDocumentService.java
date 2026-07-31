package com.hotel.propertycommerce.invoice;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/** Renders a deterministic, dependency-free PDF from an immutable invoice snapshot. */
@Service
public class PropertyInvoiceDocumentService {

    public byte[] renderPdf(
            PropertyInvoice invoice,
            List<PropertyInvoiceLine> lines,
            List<PropertyCreditNote> creditNotes) {
        if (invoice == null || invoice.getStatus() != PropertyInvoice.Status.FINALIZED) {
            throw new IllegalArgumentException("Only finalized invoices can be rendered.");
        }

        List<String> text = new ArrayList<>();
        text.add("LuxeStay Property Invoice");
        text.add("Invoice: " + invoice.getInvoiceNumber());
        text.add("Status: " + invoice.getStatus().name());
        text.add("Reservation: " + invoice.getReservation().getId());
        text.add("Total VND: " + invoice.getTotalAmount());
        text.add("Paid VND: " + invoice.getPaidAmount());
        text.add("Balance VND: " + invoice.getBalanceAmount());
        text.add("Lines: " + (lines == null ? 0 : lines.size()));
        if (creditNotes != null && !creditNotes.isEmpty()) {
            text.add("Credit notes: " + creditNotes.size());
        }
        return simplePdf(text);
    }

    private byte[] simplePdf(List<String> lines) {
        List<String> contentLines = new ArrayList<>();
        int y = 770;
        for (String line : lines) {
            contentLines.add("BT /F1 10 Tf 50 " + y + " Td (" + escape(toAscii(line)) + ") Tj ET");
            y -= 18;
            if (y < 40) {
                break;
            }
        }
        byte[] stream = String.join("\n", contentLines).getBytes(StandardCharsets.ISO_8859_1);

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            write(out, "%PDF-1.4\n%âãÏÓ\n");
            List<Integer> offsets = new ArrayList<>();
            offsets.add(0);
            offsets.add(object(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"));
            offsets.add(object(out, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"));
            offsets.add(object(out, "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                    + "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n"));
            offsets.add(object(out, "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n"));
            offsets.add(object(out, "5 0 obj\n<< /Length " + stream.length + " >>\nstream\n"));
            out.write(stream);
            write(out, "\nendstream\nendobj\n");
            int xref = out.size();
            write(out, "xref\n0 6\n0000000000 65535 f \n");
            for (int i = 1; i < offsets.size(); i++) {
                write(out, String.format("%010d 00000 n \n", offsets.get(i)));
            }
            write(out, "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to render invoice PDF.", exception);
        }
    }

    private int object(ByteArrayOutputStream out, String value) throws IOException {
        int offset = out.size();
        write(out, value);
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
}
