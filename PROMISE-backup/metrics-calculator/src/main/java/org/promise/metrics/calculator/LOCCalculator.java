package org.promise.metrics.calculator;

import org.eclipse.jdt.core.dom.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

/**
 * Calculator for Lines of Code (LOC).
 * PROMISE dataset LOC: counts all non-blank lines within the class body,
 * including lines with code (even if they also have comments).
 * Pure comment-only lines are excluded.
 */
public class LOCCalculator {

    /**
     * Calculate LOC for a compilation unit (class).
     *
     * @param compilationUnit The parsed Java file
     * @param sourceCode      The original source code
     * @return Number of lines of code (excluding blanks and pure comment lines)
     */
    public static int calculateLOC(CompilationUnit compilationUnit, String sourceCode) {
        // Get all types (classes, interfaces, enums) in the file
        @SuppressWarnings("unchecked")
        List<AbstractTypeDeclaration> types = compilationUnit.types();

        if (types.isEmpty()) {
            return 0;
        }

        // For now, calculate LOC for the entire file
        // (In case of multiple classes in one file, this gives the total)
        int startLine = compilationUnit.getLineNumber(0);
        int endPosition = compilationUnit.getLength() - 1;
        int endLine = compilationUnit.getLineNumber(endPosition);

        String[] lines = sourceCode.split("\n", -1);

        return countLOCInRange(lines, startLine, endLine);
    }

    /**
     * Calculate LOC for a specific type declaration (class/interface/enum).
     * Uses PROMISE dataset definition: counts non-blank, non-pure-comment lines.
     *
     * @param compilationUnit The parsed Java file
     * @param typeDeclaration The type to calculate LOC for
     * @param sourceCode      The original source code
     * @return Number of lines of code for the type
     */
    public static int calculateLOCForType(CompilationUnit compilationUnit,
                                          AbstractTypeDeclaration typeDeclaration,
                                          String sourceCode) {
        int startPos = typeDeclaration.getStartPosition();
        int endPos = startPos + typeDeclaration.getLength() - 1;

        int startLine = compilationUnit.getLineNumber(startPos);
        int endLine = compilationUnit.getLineNumber(endPos);

        String[] lines = sourceCode.split("\n", -1);

        return countLOCInRange(lines, startLine, endLine);
    }

    /**
     * Count LOC in a specific line range.
     * Counts non-blank lines that are not pure comment lines.
     * Lines with both code and comments are counted as LOC.
     *
     * @param lines     Array of source lines (0-indexed)
     * @param startLine Start line number (1-indexed)
     * @param endLine   End line number (1-indexed)
     * @return LOC count
     */
    private static int countLOCInRange(String[] lines, int startLine, int endLine) {
        int loc = 0;
        boolean inBlockComment = false;

        for (int lineNum = startLine; lineNum <= endLine; lineNum++) {
            if (lineNum < 1 || lineNum > lines.length) {
                continue;
            }

            String line = lines[lineNum - 1]; // Convert to 0-indexed
            String trimmed = line.trim();

            // Skip blank lines
            if (trimmed.isEmpty()) {
                continue;
            }

            // Handle block comments
            if (inBlockComment) {
                if (trimmed.contains("*/")) {
                    inBlockComment = false;
                    // Check if there's code after the comment ends
                    int endIdx = trimmed.indexOf("*/") + 2;
                    if (endIdx < trimmed.length()) {
                        String afterComment = trimmed.substring(endIdx).trim();
                        if (!afterComment.isEmpty() && !afterComment.startsWith("//") && !afterComment.startsWith("/*")) {
                            loc++;
                        }
                    }
                }
                continue;
            }

            // Check for block comment start
            if (trimmed.startsWith("/*")) {
                // Check if it ends on the same line
                if (trimmed.contains("*/")) {
                    int endIdx = trimmed.indexOf("*/") + 2;
                    // Check for code before the comment
                    // Actually, if line starts with /*, there's no code before
                    // Check for code after
                    if (endIdx < trimmed.length()) {
                        String afterComment = trimmed.substring(endIdx).trim();
                        if (!afterComment.isEmpty() && !afterComment.startsWith("//") && !afterComment.startsWith("/*")) {
                            loc++;
                        }
                    }
                } else {
                    inBlockComment = true;
                }
                continue;
            }

            // Check for single-line comment
            if (trimmed.startsWith("//")) {
                continue;
            }

            // Check for line that starts with * (continuation of Javadoc/block comment)
            if (trimmed.startsWith("*")) {
                continue;
            }

            // This is a code line (may contain inline comments, but has code)
            loc++;
        }

        return loc;
    }

    /**
     * Alternative simpler LOC calculation (just counts non-blank, non-comment lines).
     * This might be more accurate for some definitions of LOC.
     */
    public static int calculateSimpleLOC(String sourceCode) {
        int loc = 0;
        boolean inBlockComment = false;

        try (BufferedReader reader = new BufferedReader(new StringReader(sourceCode))) {
            String line;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                // Skip blank lines
                if (trimmed.isEmpty()) {
                    continue;
                }

                // Handle block comments
                if (inBlockComment) {
                    if (trimmed.contains("*/")) {
                        inBlockComment = false;
                        // Check if there's code after the comment on the same line
                        String afterComment = trimmed.substring(trimmed.indexOf("*/") + 2).trim();
                        if (!afterComment.isEmpty() && !afterComment.startsWith("//")) {
                            loc++;
                        }
                    }
                    continue;
                }

                if (trimmed.startsWith("/*")) {
                    if (!trimmed.contains("*/")) {
                        inBlockComment = true;
                    }
                    continue;
                }

                // Skip single-line comments
                if (trimmed.startsWith("//")) {
                    continue;
                }

                // Count as LOC
                loc++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return loc;
    }
}
