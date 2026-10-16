package com.hls.payroll.run.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.boot.json.JsonParserFactory;

/**
 * Draws one payslip (spec 013b US2, FR-017): a stored line in, PDF bytes out. Pure: it reads nothing live, so the
 * same line always gives the same figures. A4 with the bundled Noto Sans (the standard PDF fonts have no rupee sign);
 * rupees carry Indian digit grouping and dates read DD/MM/YYYY. Long lists continue on further pages.
 */
final class PayslipRenderer {

    /** Everything a payslip shows, taken from the stored line and the run. */
    record Data(
            String monthName,
            String runName,
            String personName,
            String personLabel,
            int payableDays,
            BigDecimal workingDays,
            BigDecimal unpaidDays,
            BigDecimal earned,
            BigDecimal deduction,
            BigDecimal adjustments,
            BigDecimal net,
            String workingJson,
            LocalDate approvedOn) {}

    private static final float MARGIN = 48;
    private static final float FONT = 10;
    private static final float LEADING = 15;

    private PayslipRenderer() {}

    static byte[] render(Data data) {
        try (PDDocument document = new PDDocument()) {
            PDFont regular = load(document, "/fonts/NotoSans-Regular.ttf");
            PDFont bold = load(document, "/fonts/NotoSans-Bold.ttf");
            Page page = new Page(document, regular, bold);
            draw(page, data);
            page.close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("The payslip could not be drawn", e);
        }
    }

    private static PDFont load(PDDocument document, String resource) throws IOException {
        try (InputStream in = PayslipRenderer.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("The payslip font " + resource + " is missing");
            }
            return PDType0Font.load(document, in);
        }
    }

    // ------------------------------------------------------------------ content

    @SuppressWarnings("unchecked")
    private static void draw(Page page, Data d) throws IOException {
        Map<String, Object> working = JsonParserFactory.getJsonParser().parseMap(d.workingJson());

        page.text("Payslip", 18, true);
        page.text(d.monthName() + " - " + d.runName(), 11, false);
        page.gap(8);
        page.pair("Name", d.personName(), true);
        if (d.personLabel() != null) {
            page.pair("Role", d.personLabel(), false);
        }
        page.pair("Approved on", RunInput.date(d.approvedOn()), false);
        page.rule();

        page.heading("Salary");
        for (Map<String, Object> row : (List<Map<String, Object>>) working.get("salaryRows")) {
            String from = RunInput.date(LocalDate.parse((String) row.get("from")));
            String to = RunInput.date(LocalDate.parse((String) row.get("to")));
            page.pair(
                    from.equals(to) ? from : from + " to " + to,
                    RunInput.rupees(new BigDecimal((String) row.get("monthly"))) + " a month",
                    false);
        }
        page.gap(4);
        page.pair("Days paid", String.valueOf(d.payableDays()), false);
        page.pair("Working days in the month", plain(d.workingDays()), false);
        page.pair("Unpaid days", plain(d.unpaidDays()), false);
        page.rule();

        Map<String, Object> earned = (Map<String, Object>) working.get("earned");
        page.heading("Earned pay");
        page.pair("Before rounding", RunInput.rupees(new BigDecimal((String) earned.get("unrounded"))), false);
        page.pair("Earned pay", RunInput.rupees(d.earned()), true);
        page.rule();

        page.heading("Deduction for days without pay");
        List<Map<String, Object>> unpaid = (List<Map<String, Object>>) working.get("unpaid");
        if (unpaid.isEmpty()) {
            page.text("No unpaid days.", FONT, false);
        }
        for (Map<String, Object> day : unpaid) {
            String reason = (String) day.get("reason");
            String left = RunInput.date(LocalDate.parse((String) day.get("date")))
                    + "  (" + plain(new BigDecimal((String) day.get("dayValue"))) + " day"
                    + (reason == null ? "" : ", " + reason) + ")";
            page.pair(left, RunInput.rupees(new BigDecimal((String) day.get("amount"))), false);
        }
        Map<String, Object> deduction = (Map<String, Object>) working.get("deduction");
        if (!unpaid.isEmpty()) {
            page.pair("Before rounding", RunInput.rupees(new BigDecimal((String) deduction.get("unrounded"))), false);
        }
        page.pair("Deduction", RunInput.rupees(d.deduction()), true);
        page.rule();

        page.heading("Adjustments");
        List<Map<String, Object>> adjustments = (List<Map<String, Object>>) working.get("adjustments");
        if (adjustments.isEmpty()) {
            page.text("No adjustments.", FONT, false);
        }
        for (Map<String, Object> adjustment : adjustments) {
            page.pair(
                    kindName((String) adjustment.get("kind")) + ": " + adjustment.get("reason"),
                    RunInput.rupees(new BigDecimal((String) adjustment.get("amount"))),
                    false);
        }
        if (!adjustments.isEmpty()) {
            page.pair("Adjustments", RunInput.rupees(d.adjustments()), true);
        }
        page.rule();

        page.pair("Net pay", RunInput.rupees(d.net()), true, 13);
        page.gap(2);
        page.text("Net pay is earned pay, less the deduction, plus the adjustments.", 8, false);
    }

    private static String kindName(String kind) {
        return switch (kind) {
            case "CORRECTION" -> "Correction";
            case "INCENTIVE" -> "Incentive";
            case "REIMBURSEMENT" -> "Reimbursement";
            case "SUBSTITUTE_CREDIT" -> "Substitute credit";
            default -> kind;
        };
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    // -------------------------------------------------------------------- pages

    /** A cursor over A4 pages: writes lines top to bottom and starts a new page when the next line does not fit. */
    private static final class Page {
        private final PDDocument document;
        private final PDFont regular;
        private final PDFont bold;
        private PDPageContentStream content;
        private float y;
        private final float width = PDRectangle.A4.getWidth();

        Page(PDDocument document, PDFont regular, PDFont bold) throws IOException {
            this.document = document;
            this.regular = regular;
            this.bold = bold;
            next();
        }

        private void next() throws IOException {
            if (content != null) {
                content.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
        }

        void close() throws IOException {
            content.close();
        }

        private void room(float needed) throws IOException {
            if (y - needed < MARGIN) {
                next();
            }
        }

        void gap(float amount) {
            y -= amount;
        }

        void rule() throws IOException {
            room(12);
            y -= 4;
            content.setLineWidth(0.5f);
            content.moveTo(MARGIN, y);
            content.lineTo(width - MARGIN, y);
            content.stroke();
            y -= 10;
        }

        void heading(String text) throws IOException {
            text(text, 12, true);
            gap(2);
        }

        void text(String text, float size, boolean strong) throws IOException {
            for (String line : wrap(text, strong ? bold : regular, size, width - 2 * MARGIN)) {
                room(size + 5);
                draw(line, MARGIN, size, strong);
                y -= size + 5;
            }
        }

        void pair(String left, String right, boolean strong) throws IOException {
            pair(left, right, strong, FONT);
        }

        /** The label on the left, wrapped if long, and the value right-aligned on the first line. */
        void pair(String left, String right, boolean strong, float size) throws IOException {
            PDFont font = strong ? bold : regular;
            float rightWidth = width(right, font, size);
            List<String> lines = wrap(left, font, size, width - 2 * MARGIN - rightWidth - 12);
            for (int i = 0; i < lines.size(); i++) {
                room(size + 5);
                draw(lines.get(i), MARGIN, size, strong);
                if (i == 0) {
                    draw(right, width - MARGIN - rightWidth, size, strong);
                }
                y -= size + 5;
            }
        }

        private void draw(String text, float x, float size, boolean strong) throws IOException {
            content.beginText();
            content.setFont(strong ? bold : regular, size);
            content.newLineAtOffset(x, y);
            content.showText(text);
            content.endText();
        }

        private static float width(String text, PDFont font, float size) throws IOException {
            return font.getStringWidth(text) / 1000 * size;
        }

        private static List<String> wrap(String text, PDFont font, float size, float max) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (width(candidate, font, size) > max && line.length() > 0) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            lines.add(line.toString());
            return lines;
        }
    }
}
