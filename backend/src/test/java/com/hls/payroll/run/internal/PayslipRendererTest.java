package com.hls.payroll.run.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** Spec 013b T032: the payslip carries every figure of the stored line and reads nothing live. */
class PayslipRendererTest {

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static String working(int unpaidDays, boolean withAdjustment) {
        List<Object> unpaid = new ArrayList<>();
        for (int i = 0; i < unpaidDays; i++) {
            unpaid.add(map(
                    "date", LocalDate.of(2026, 9, 1 + i).toString(),
                    "dayValue", "1",
                    "salary", "125000.00",
                    "divisor", 20,
                    "amount", "6250.0000000000",
                    "reason", "Full day without pay"));
        }
        List<Object> adjustments = withAdjustment
                ? List.of(map("id", "a1", "kind", "CORRECTION", "amount", "2000.00", "reason", "August underpaid"))
                : List.of();
        return Json.write(map(
                "state", "PAYABLE",
                "salaryRows", List.of(
                        map("from", "2026-09-01", "to", "2026-09-15", "monthly", "100000.00"),
                        map("from", "2026-09-16", "to", "2026-09-30", "monthly", "125000.00")),
                "earned", map("unrounded", "112500.0000000000", "rounded", "112500.00"),
                "deduction", map("unrounded", "12500.0000000000", "rounded", "12500.00"),
                "unpaid", unpaid,
                "adjustments", adjustments));
    }

    private static PayslipRenderer.Data data(int unpaidDays, boolean adjustment) {
        BigDecimal adjustments = adjustment ? new BigDecimal("2000.00") : BigDecimal.ZERO.setScale(2);
        return new PayslipRenderer.Data(
                "September 2026",
                "Primary run",
                "Tara Teacher",
                "Teacher",
                20,
                new BigDecimal("20"),
                new BigDecimal(unpaidDays),
                new BigDecimal("112500.00"),
                new BigDecimal("12500.00"),
                adjustments,
                new BigDecimal("112500.00").subtract(new BigDecimal("12500.00")).add(adjustments),
                working(unpaidDays, adjustment),
                LocalDate.of(2026, 10, 5));
    }

    private static String textOf(byte[] pdf) throws Exception {
        try (PDDocument read = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(read);
        }
    }

    @Test
    void everyFigureOfTheLineIsOnThePayslip() throws Exception {
        byte[] pdf = PayslipRenderer.render(data(2, true));

        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        String text = textOf(pdf);
        assertThat(text)
                .contains("Payslip")
                .contains("September 2026")
                .contains("Tara Teacher")
                .contains("Teacher")
                .contains("05/10/2026")
                // salary rows with the dates each applied to
                .contains("01/09/2026 to 15/09/2026")
                .contains("₹1,00,000.00 a month")
                .contains("16/09/2026 to 30/09/2026")
                .contains("₹1,25,000.00 a month")
                // earned, each unpaid day with its value and reason, the deduction, the adjustment, the net
                .contains("₹1,12,500.00")
                .contains("01/09/2026")
                .contains("02/09/2026")
                .contains("Full day without pay")
                .contains("₹6,250.00")
                .contains("₹12,500.00")
                .contains("Correction: August underpaid")
                .contains("₹2,000.00")
                .contains("₹1,02,000.00");
    }

    @Test
    void aPersonWithNoUnpaidDaysOrAdjustmentsSaysSo() throws Exception {
        String text = textOf(PayslipRenderer.render(data(0, false)));

        assertThat(text).contains("No unpaid days.").contains("No adjustments.");
    }

    @Test
    void aLongListContinuesOnFurtherPagesWithoutLosingAnyDay() throws Exception {
        byte[] pdf = PayslipRenderer.render(data(28, true));

        try (PDDocument read = Loader.loadPDF(pdf)) {
            assertThat(read.getNumberOfPages()).isGreaterThan(1);
        }
        String text = textOf(pdf);
        assertThat(text).contains("01/09/2026").contains("28/09/2026").contains("₹1,02,000.00");
    }

    @Test
    void theSameLineGivesTheSameFigures() throws Exception {
        assertThat(textOf(PayslipRenderer.render(data(3, true)))).isEqualTo(textOf(PayslipRenderer.render(data(3, true))));
    }
}
