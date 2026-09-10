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
    private static final float BODY_SIZE = 8.5f;
    private static final float LEADING = 12.5f;
    private static final float TITLE_SIZE = 17f;
    private static final float HEADING_SIZE = 11.5f;
    private static final float CELL_PADDING = 3.5f;
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
            addPageNumbersAndFooters(document, title);
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
                writer.table(table);
            }
            writer.close();
            addPageNumbersAndFooters(document, title);
            document.save(target.toFile());
        }
    }

    /** Adds running footers and page numbering to every page of the report. */
    private static void addPageNumbersAndFooters(PDDocument document, String title) throws IOException {
        int totalPages = document.getNumberOfPages();
        for (int i = 0; i < totalPages; i++) {
            PDPage page = document.getPage(i);
            float width = page.getMediaBox().getWidth();
            try (PDPageContentStream stream = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                // Subtle footer rule
                stream.setStrokingColor(0.82f, 0.84f, 0.88f);
                stream.setLineWidth(0.5f);
                stream.moveTo(MARGIN, 28f);
                stream.lineTo(width - MARGIN, 28f);
                stream.stroke();

                // Brand / document title on left
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 7.5f);
                stream.setNonStrokingColor(0.48f, 0.52f, 0.58f);
                stream.newLineAtOffset(MARGIN, 17f);
                stream.showText("DefectLab Analytics Platform | " + safe(title));
                stream.endText();

                // Page count on right
                String pageText = "Page " + (i + 1) + " of " + totalPages;
                float textWidth = PDType1Font.HELVETICA.getStringWidth(pageText) / 1000f * 7.5f;
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 7.5f);
                stream.setNonStrokingColor(0.48f, 0.52f, 0.58f);
                stream.newLineAtOffset(width - MARGIN - textWidth, 17f);
                stream.showText(pageText);
                stream.endText();
            }
        }
    }

    /** One table section: an optional heading, column headers, rows, and optional custom widths. */
    public static final class Table {
        private final String heading;
        private final List<String> headers;
        private final List<List<String>> rows;
        private final float[] columnWeights;

        public Table(String heading, List<String> headers, List<List<String>> rows) {
            this(heading, headers, rows, null);
        }

        public Table(String heading, List<String> headers, List<List<String>> rows, float[] columnWeights) {
            this.heading = heading;
            this.headers = headers;
            this.rows = rows;
            this.columnWeights = columnWeights;
        }

        public String heading() { return heading; }
        public List<String> headers() { return headers; }
        public List<List<String>> rows() { return rows; }
        public float[] columnWeights() { return columnWeights; }
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
                    int fit = 1;
                    while (fit < current.length() && width(current.substring(0, fit + 1), font, fontSize) <= maxWidth) {
                        fit++;
                    }
                    lines.add(current.substring(0, fit));
                    current = new StringBuilder(current.substring(fit));
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

        private List<String> activeHeaders;
        private float[] activeWidths;

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

            // When repeating headers inside a multi-page table
            if (activeHeaders != null && activeWidths != null) {
                drawHeaderRow(activeHeaders, activeWidths, false);
            }
        }

        private void ensureRoom(float needed) throws IOException {
            // Preserve 40pt at bottom for page footer
            if (y - needed < MARGIN + 15f) {
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
            ensureRoom(36f);
            stream.setNonStrokingColor(0.08f, 0.12f, 0.18f);
            text(MARGIN, y, title, PDType1Font.HELVETICA_BOLD, TITLE_SIZE);
            y -= 14f;

            // Brand caption
            stream.setNonStrokingColor(0.45f, 0.50f, 0.56f);
            String caption = title != null && title.toLowerCase().contains("comparison")
                    ? "Software Metrics Analysis & Predefined Baseline Comparison"
                    : "Software Defect Prediction & Risk Ranking Analysis";
            text(MARGIN, y, caption, PDType1Font.HELVETICA_OBLIQUE, 8.5f);
            y -= 14f;

            // Subtle divider rule
            stream.setStrokingColor(0.80f, 0.83f, 0.88f);
            stream.setLineWidth(1f);
            stream.moveTo(MARGIN, y);
            stream.lineTo(pageWidth - MARGIN, y);
            stream.stroke();
            stream.setNonStrokingColor(0.15f, 0.18f, 0.22f);
            y -= 14f;
        }

        private void sectionHeading(String heading) throws IOException {
            ensureRoom(24f);
            stream.setNonStrokingColor(0.12f, 0.16f, 0.22f);
            text(MARGIN, y, heading, PDType1Font.HELVETICA_BOLD, HEADING_SIZE);
            y -= 16f;
        }

        private void paragraphLine(String line) throws IOException {
            if (line == null || line.isBlank()) {
                blank();
                return;
            }
            stream.setNonStrokingColor(0.20f, 0.24f, 0.30f);
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

        private void table(Table table) throws IOException {
            List<String> headers = table.headers();
            List<List<String>> rows = table.rows();
            if (headers == null || headers.isEmpty()) {
                return;
            }
            float tableWidth = pageWidth - 2 * MARGIN;
            float[] widths = columnWidths(headers, table.columnWeights(), tableWidth);

            this.activeHeaders = headers;
            this.activeWidths = widths;

            drawHeaderRow(headers, widths, true);
            int rowIndex = 0;
            for (List<String> row : rows) {
                drawDataRow(row, widths, rowIndex++);
            }

            this.activeHeaders = null;
            this.activeWidths = null;
        }

        private float[] columnWidths(List<String> headers, float[] customWeights, float tableWidth) {
            int columnCount = headers.size();
            float[] weights = new float[columnCount];
            if (customWeights != null && customWeights.length == columnCount) {
                System.arraycopy(customWeights, 0, weights, 0, columnCount);
            } else {
                for (int i = 0; i < columnCount; i++) {
                    weights[i] = i == 0 ? 1.6f : 1f;
                }
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

        private void drawHeaderRow(List<String> headers, float[] columnWidths, boolean checkRoom) throws IOException {
            List<List<String>> wrappedCells = new ArrayList<>();
            int maxLines = 1;
            for (int i = 0; i < columnWidths.length; i++) {
                String value = i < headers.size() ? headers.get(i) : "";
                List<String> wrapped = wrapToWidth(value, PDType1Font.HELVETICA_BOLD, BODY_SIZE, columnWidths[i] - 2 * CELL_PADDING);
                wrappedCells.add(wrapped);
                maxLines = Math.max(maxLines, wrapped.size());
            }
            float rowHeight = maxLines * LEADING + 2 * CELL_PADDING + 2f;

            if (checkRoom) {
                ensureRoom(rowHeight + LEADING);
            }

            float rowTopY = y;
            float totalWidth = sum(columnWidths);

            // Shaded header background
            stream.setNonStrokingColor(0.93f, 0.94f, 0.96f);
            stream.addRect(MARGIN, rowTopY - rowHeight, totalWidth, rowHeight);
            stream.fill();

            // Header text
            stream.setNonStrokingColor(0.12f, 0.16f, 0.22f);
            float x = MARGIN;
            for (int i = 0; i < columnWidths.length; i++) {
                float cellY = rowTopY - CELL_PADDING - BODY_SIZE;
                for (String line : wrappedCells.get(i)) {
                    text(x + CELL_PADDING, cellY, line, PDType1Font.HELVETICA_BOLD, BODY_SIZE);
                    cellY -= LEADING;
                }
                x += columnWidths[i];
            }

            // Top and bottom border lines
            float bottomY = rowTopY - rowHeight;
            stream.setStrokingColor(0.72f, 0.75f, 0.80f);
            stream.setLineWidth(1.1f);
            stream.moveTo(MARGIN, rowTopY);
            stream.lineTo(MARGIN + totalWidth, rowTopY);
            stream.stroke();

            stream.setLineWidth(1.1f);
            stream.moveTo(MARGIN, bottomY);
            stream.lineTo(MARGIN + totalWidth, bottomY);
            stream.stroke();

            y = bottomY;
        }

        private void drawDataRow(List<String> cells, float[] columnWidths, int rowIndex) throws IOException {
            List<List<String>> wrappedCells = new ArrayList<>();
            int maxLines = 1;
            for (int i = 0; i < columnWidths.length; i++) {
                String value = i < cells.size() ? cells.get(i) : "";
                List<String> wrapped = wrapToWidth(value, PDType1Font.HELVETICA, BODY_SIZE, columnWidths[i] - 2 * CELL_PADDING);
                wrappedCells.add(wrapped);
                maxLines = Math.max(maxLines, wrapped.size());
            }
            float rowHeight = maxLines * LEADING + 2 * CELL_PADDING;

            ensureRoom(rowHeight);

            float rowTopY = y;
            float totalWidth = sum(columnWidths);

            // Subtle alternating row tint
            if (rowIndex % 2 == 1) {
                stream.setNonStrokingColor(0.985f, 0.988f, 0.993f);
                stream.addRect(MARGIN, rowTopY - rowHeight, totalWidth, rowHeight);
                stream.fill();
            }

            stream.setNonStrokingColor(0.18f, 0.22f, 0.28f);
            float x = MARGIN;
            for (int i = 0; i < columnWidths.length; i++) {
                float cellY = rowTopY - CELL_PADDING - BODY_SIZE;
                for (String line : wrappedCells.get(i)) {
                    text(x + CELL_PADDING, cellY, line, PDType1Font.HELVETICA, BODY_SIZE);
                    cellY -= LEADING;
                }
                x += columnWidths[i];
            }

            // Thin row divider
            float bottomY = rowTopY - rowHeight;
            stream.setStrokingColor(0.88f, 0.89f, 0.92f);
            stream.setLineWidth(0.35f);
            stream.moveTo(MARGIN, bottomY);
            stream.lineTo(MARGIN + totalWidth, bottomY);
            stream.stroke();

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
