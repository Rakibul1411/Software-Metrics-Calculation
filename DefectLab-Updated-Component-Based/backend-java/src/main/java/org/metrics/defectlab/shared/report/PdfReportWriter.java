package org.metrics.defectlab.shared.report;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

/**
 * Small, deterministic PDF writer for downloadable workflow reports.
 * Report services supply already-formatted content so no database path is
 * ever exposed by an API response.
 */
public final class PdfReportWriter {

    private static final float MARGIN = 40f;
    private static final float BODY_SIZE = 9f;
    private static final float LEADING = 12.5f;
    private static final float TITLE_SIZE = 16f;
    private static final float HEADING_SIZE = 12f;
    private static final float CELL_PADDING = 3f;
    private static final int WRAP_AT = 105;

    private PdfReportWriter() {
    }

    /** Plain line-oriented report (still used where no tabular data applies). */
    public static void write(Path target, String title, List<String> lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PageWriter writer = new PageWriter(document);
            writer.title(title);
            for (String line : lines) {
                writer.paragraphLine(line);
            }
            writer.close();
            document.save(target.toFile());
        }
    }

    /**
     * Report built from an intro paragraph plus one or more grid tables, each
     * with its own heading. Long tables page-break automatically, and cell
     * text wraps to its column instead of overflowing.
     */
    public static void writeTables(
            Path target, String title, List<String> introLines, List<Table> tables)
            throws IOException {
        try (PDDocument document = new PDDocument()) {
            PageWriter writer = new PageWriter(document);
            writer.title(title);
            for (String line : introLines) {
                writer.paragraphLine(line);
            }
            for (Table table : tables) {
                writer.blank();
                if (table.heading != null && !table.heading.isBlank()) {
                    writer.sectionHeading(table.heading);
                }
                writer.table(table.headers, table.rows);
            }
            writer.close();
            document.save(target.toFile());
        }
    }

    /** One table section: an optional heading, column headers, and rows. */
    public static final class Table {
        private final String heading;
        private final List<String> headers;
        private final List<List<String>> rows;

        public Table(String heading, List<String> headers, List<List<String>> rows) {
            this.heading = heading;
            this.headers = headers;
            this.rows = rows;
        }
    }

    private static List<String> wrapPlain(String value) {
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

    /** Word-wraps text to a pixel width, measured in the given font. */
    private static List<String> wrapToWidth(String value, PDFont font, float fontSize, float maxWidth) {
        List<String> lines = new ArrayList<>();
        String text = value == null ? "" : safe(value);
        if (text.isEmpty()) {
            lines.add("");
            return lines;
        }
        for (String paragraph : text.split("\n", -1)) {
            String[] words = paragraph.split(" ");
            StringBuilder current = new StringBuilder();
            for (String word : words) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (width(candidate, font, fontSize) <= maxWidth || current.isEmpty()) {
                    current = new StringBuilder(candidate);
                } else {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                }
                while (width(current.toString(), font, fontSize) > maxWidth && current.length() > 1) {
                    // A single token is still too wide (e.g. a long identifier):
                    // hard-split it rather than overflow the column.
                    lines.add(current.substring(0, current.length() - 1));
                    current = new StringBuilder(current.substring(current.length() - 1));
                }
            }
            lines.add(current.toString());
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    private static float width(String text, PDFont font, float fontSize) {
        try {
            return font.getStringWidth(text) / 1000f * fontSize;
        } catch (IOException | IllegalArgumentException exception) {
            return text.length() * fontSize * 0.6f;
        }
    }

    private static String safe(String value) {
        return value.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private static final class PageWriter {
        private final PDDocument document;
        private PDPage page;
        private PDPageContentStream stream;
        private float y;
        private float pageWidth;

        private PageWriter(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            pageWidth = page.getMediaBox().getWidth();
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private void ensureRoom(float needed) throws IOException {
            if (y - needed < MARGIN) {
                newPage();
            }
        }

        private void text(float x, float baselineY, String value, PDFont font, float size) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, baselineY);
            stream.showText(safe(value));
            stream.endText();
        }

        private void title(String title) throws IOException {
            ensureRoom(30f);
            text(MARGIN, y, title, PDType1Font.HELVETICA_BOLD, TITLE_SIZE);
            y -= 26f;
        }

        private void sectionHeading(String heading) throws IOException {
            ensureRoom(20f);
            text(MARGIN, y, heading, PDType1Font.HELVETICA_BOLD, HEADING_SIZE);
            y -= 18f;
        }

        private void paragraphLine(String line) throws IOException {
            if (line == null || line.isBlank()) {
                blank();
                return;
            }
            for (String wrapped : wrapPlain(line)) {
                ensureRoom(LEADING);
                text(MARGIN, y, wrapped, PDType1Font.HELVETICA, BODY_SIZE);
                y -= LEADING;
            }
        }

        private void blank() throws IOException {
            ensureRoom(LEADING);
            y -= LEADING;
        }

        private void table(List<String> headers, List<List<String>> rows) throws IOException {
            if (headers == null || headers.isEmpty()) {
                return;
            }
            float tableWidth = pageWidth - 2 * MARGIN;
            float[] columnWidths = columnWidths(headers.size(), tableWidth);

            drawRow(headers, columnWidths, PDType1Font.HELVETICA_BOLD, true);
            for (List<String> row : rows) {
                drawRow(row, columnWidths, PDType1Font.HELVETICA, false);
            }
        }

        /** The first column (usually a name/identifier) gets extra room. */
        private float[] columnWidths(int columnCount, float tableWidth) {
            float[] weights = new float[columnCount];
            for (int i = 0; i < columnCount; i++) {
                weights[i] = i == 0 ? 1.6f : 1f;
            }
            float totalWeight = 0f;
            for (float weight : weights) {
                totalWeight += weight;
            }
            float[] widths = new float[columnCount];
            for (int i = 0; i < columnCount; i++) {
                widths[i] = tableWidth * weights[i] / totalWeight;
            }
            return widths;
        }

        private void drawRow(
                List<String> cells, float[] columnWidths, PDFont font, boolean header) throws IOException {
            List<List<String>> wrappedCells = new ArrayList<>();
            int maxLines = 1;
            for (int i = 0; i < columnWidths.length; i++) {
                String value = i < cells.size() ? cells.get(i) : "";
                List<String> wrapped = wrapToWidth(value, font, BODY_SIZE, columnWidths[i] - 2 * CELL_PADDING);
                wrappedCells.add(wrapped);
                maxLines = Math.max(maxLines, wrapped.size());
            }
            float rowHeight = maxLines * LEADING + 2 * CELL_PADDING;

            // A header must never be orphaned alone at the bottom of a page:
            // if we're about to page-break right after drawing it, start fresh.
            ensureRoom(header ? rowHeight + LEADING : rowHeight);

            float rowTopY = y;
            float x = MARGIN;
            for (int i = 0; i < columnWidths.length; i++) {
                float cellY = rowTopY - CELL_PADDING - BODY_SIZE;
                for (String line : wrappedCells.get(i)) {
                    text(x + CELL_PADDING, cellY, line, font, BODY_SIZE);
                    cellY -= LEADING;
                }
                x += columnWidths[i];
            }

            float bottomY = rowTopY - rowHeight;
            stream.setLineWidth(header ? 1f : 0.4f);
            stream.moveTo(MARGIN, bottomY);
            stream.lineTo(MARGIN + sum(columnWidths), bottomY);
            stream.stroke();
            if (header) {
                stream.moveTo(MARGIN, rowTopY);
                stream.lineTo(MARGIN + sum(columnWidths), rowTopY);
                stream.stroke();
            }
            y = bottomY;
        }

        private float sum(float[] values) {
            float total = 0f;
            for (float value : values) {
                total += value;
            }
            return total;
        }

        private void close() throws IOException {
            if (stream != null) {
                stream.close();
            }
        }
    }
}
