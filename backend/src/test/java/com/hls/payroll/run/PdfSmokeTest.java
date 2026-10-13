package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** Spec 013b T002: the bundled font prints the rupee sign and Indian digit grouping, and the PDF can be read back. */
class PdfSmokeTest {

    @Test
    void theBundledFontPrintsTheRupeeSignAndIndianGrouping() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument();
                InputStream font = getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf")) {
            assertThat(font).as("the bundled font").isNotNull();
            PDType0Font noto = PDType0Font.load(document, font);
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(noto, 12);
                content.newLineAtOffset(50, 700);
                content.showText("Net pay ₹1,25,000.00");
                content.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            pdf = out.toByteArray();
        }

        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        try (PDDocument read = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(read)).contains("Net pay ₹1,25,000.00");
        }
    }
}
