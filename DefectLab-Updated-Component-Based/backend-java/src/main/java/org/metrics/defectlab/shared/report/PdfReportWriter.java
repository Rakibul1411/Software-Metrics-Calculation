package org.metrics.defectlab.shared.report;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

/**
 * Small, deterministic PDF writer for downloadable workflow reports.
 * Report services supply already-formatted lines so no database path is ever
 * exposed by an API response.
 */
public final class PdfReportWriter {

    private static final float MARGIN = 45f;
    private static final float BODY_SIZE = 9f;
    private static final float LEADING = 12f;
    private static final int WRAP_AT = 105;

    private PdfReportWriter() {
    }

    public static void write(Path target, String title, List<String> lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PageWriter writer = new PageWriter(document);
            writer.title(title);
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    writer.blank();
                } else {
                    for (String wrapped : wrap(line)) {
                        writer.line(wrapped);
                    }
                }
            }
            writer.close();
            document.save(target.toFile());
        }
    }

    private static List<String> wrap(String value) {
        List<String> result = new ArrayList<>();
        String remaining = value.replace('\t', ' ').trim();
        while (remaining.length() > WRAP_AT) {
            int split = remaining.lastIndexOf(' ', WRAP_AT);
            if (split < 20) {
                split = WRAP_AT;
            }
            result.add(remaining.substring(0, split).trim());
            remaining = remaining.substring(split).trim();
        }
        result.add(remaining);
        return result;
    }

    private static final class PageWriter {
        private final PDDocument document;
        private PDPageContentStream stream;
        private float y;

        private PageWriter(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.endText();
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            stream.beginText();
            stream.setFont(PDType1Font.HELVETICA, BODY_SIZE);
            stream.setLeading(LEADING);
            stream.newLineAtOffset(MARGIN, page.getMediaBox().getHeight() - MARGIN);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private void title(String title) throws IOException {
            stream.setFont(PDType1Font.HELVETICA_BOLD, 16f);
            stream.showText(safe(title));
            stream.newLineAtOffset(0, -24f);
            stream.setFont(PDType1Font.HELVETICA, BODY_SIZE);
            y -= 24f;
        }

        private void line(String line) throws IOException {
            ensureRoom();
            stream.showText(safe(line));
            stream.newLine();
            y -= LEADING;
        }

        private void blank() throws IOException {
            ensureRoom();
            stream.newLine();
            y -= LEADING;
        }

        private void ensureRoom() throws IOException {
            if (y <= MARGIN + LEADING) {
                newPage();
            }
        }

        private void close() throws IOException {
            if (stream != null) {
                stream.endText();
                stream.close();
            }
        }

        private static String safe(String value) {
            return value.replaceAll("[^\\x20-\\x7E]", "?");
        }
    }
}
