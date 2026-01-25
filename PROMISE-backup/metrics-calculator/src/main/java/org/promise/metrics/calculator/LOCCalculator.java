package org.promise.metrics.calculator;

import org.eclipse.jdt.core.dom.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

/**
 * Calculator for Lines of Code (LOC).
 * This implementation counts ALL non-blank lines inside the class body,
 * including all comments (Javadoc, block comments, line comments).
 * Lines outside the class (license headers, package, imports) are excluded.
 */
public class LOCCalculator {

    /**
     * Calculate LOC for a compilation unit (entire file).
     *
     * @param compilationUnit The parsed Java file
     * @param sourceCode      The original source code
     * @return Number of non-blank lines inside all class bodies
     */
    public static int calculateLOC(CompilationUnit compilationUnit, String sourceCode) {
        @SuppressWarnings("unchecked")
        List<AbstractTypeDeclaration> types = compilationUnit.types();

        if (types.isEmpty()) {
            return 0;
        }

        int totalLOC = 0;
        for (AbstractTypeDeclaration type : types) {
            totalLOC += calculateLOCForType(compilationUnit, type, sourceCode);
        }

        return totalLOC;
    }

    /**
     * Calculate LOC for a specific type declaration (class/interface/enum).
     * Counts ALL non-blank lines INSIDE the class body (between { and }),
     * including all comments inside the class body.
     * Comments outside the class body (like Javadoc before class declaration) are excluded.
     *
     * @param compilationUnit The parsed Java file
     * @param typeDeclaration The type to calculate LOC for
     * @param sourceCode      The original source code
     * @return Number of non-blank lines inside the class body (including comments)
     */
    public static int calculateLOCForType(CompilationUnit compilationUnit,
                                          AbstractTypeDeclaration typeDeclaration,
                                          String sourceCode) {
        int startPos = typeDeclaration.getStartPosition();
        int endPos = startPos + typeDeclaration.getLength() - 1;

        int typeStartLine = compilationUnit.getLineNumber(startPos);
        int endLine = compilationUnit.getLineNumber(endPos);

        String[] lines = sourceCode.split("\n", -1);

        // Find the line with the opening brace '{' - this is where the class body starts
        int bodyStartLine = findOpeningBraceLine(lines, typeStartLine, endLine);

        // Count all non-blank lines from the '{' line to the '}' line (inclusive)
        return countNonBlankLinesInRange(lines, bodyStartLine, endLine);
    }

    /**
     * Find the line number containing the opening brace '{' of the class body.
     *
     * @param lines     Array of source lines (0-indexed)
     * @param startLine Start line to search from (1-indexed)
     * @param endLine   End line to search to (1-indexed)
     * @return Line number (1-indexed) containing the opening brace
     */
    private static int findOpeningBraceLine(String[] lines, int startLine, int endLine) {
        for (int lineNum = startLine; lineNum <= endLine; lineNum++) {
            if (lineNum < 1 || lineNum > lines.length) {
                continue;
            }
            String line = lines[lineNum - 1];
            if (line.contains("{")) {
                return lineNum;
            }
        }
        return startLine; // Fallback
    }

    /**
     * Count ALL non-blank lines in a specific line range.
     * Counts everything including comments.
     *
     * @param lines     Array of source lines (0-indexed)
     * @param startLine Start line number (1-indexed)
     * @param endLine   End line number (1-indexed)
     * @return Number of non-blank lines
     */
    private static int countNonBlankLinesInRange(String[] lines, int startLine, int endLine) {
        int loc = 0;

        for (int lineNum = startLine; lineNum <= endLine; lineNum++) {
            if (lineNum < 1 || lineNum > lines.length) {
                continue;
            }

            String line = lines[lineNum - 1];
            // Count any line that has at least one non-whitespace character
            if (!line.trim().isEmpty()) {
                loc++;
            }
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
