package bithazard.game.grimrock.parse;

import bithazard.game.grimrock.utils.FileUtils;
import bithazard.game.grimrock.utils.LuaUtils;
import org.luaj.vm2.ast.Exp;
import org.luaj.vm2.parser.Token;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ErrorCollector {
    private static final Pattern START_OF_LINE = Pattern.compile("(?m)^");
    private final Set<Error> errors = new LinkedHashSet<>();
    private final String filename;
    private final int lineOffset;
    private final int columnOffset;

    private static final class Position {
        private final int lineNumber;
        private final int columnNumber;

        private Position(int lineNumber, int columnNumber) {
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public int getColumnNumber() {
            return columnNumber;
        }
    }

    private static final class Error {
        private final Position position;
        private final String message;
        private String filename;

        private Error(String filename, String message) {
            this.filename = filename;
            this.position = null;
            this.message = message;
        }

        private Error(String filename, int lineNumber, int columnNumber, String message) {
            this.filename = filename;
            this.position = new Position(lineNumber, columnNumber);
            this.message = message;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        @Override
        public String toString() {
            if (position == null) {
                return "(" + filename + ") " + message;
            }
            return "(" + filename + ":" + position.getLineNumber() + ":" + position.getColumnNumber() + ") " + message;
        }
    }

    public ErrorCollector() {
        this(null);
    }

    public ErrorCollector(String filename) {
        this(filename, 1, 1);
    }

    private ErrorCollector(String filename, int lineOffset, int columnOffset) {
        this.filename = filename;
        this.lineOffset = lineOffset;
        this.columnOffset = columnOffset;
    }

    public ErrorCollector createSubErrorCollector(int lineOffset, int columnOffset) {
        return new ErrorCollector(filename, lineOffset, columnOffset);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public Collection<String> getErrors() {
        return errors.stream().map(Error::toString).collect(Collectors.toList());
    }

    public void addError(String message) {
        errors.add(new Error(filename, message));
    }

    public void addError(String message, Exp syntaxElement) {
        if (syntaxElement == null) {
            addError(message);
        } else {
            String codeSnippet = START_OF_LINE.matcher(LuaUtils.expToString(syntaxElement)).replaceAll("        ");
            errors.add(createErrorWithOffset(syntaxElement.beginLine - 1, syntaxElement.beginColumn, message + "\n" + codeSnippet));
        }
    }

    public void addError(String message, Token token) {
        errors.add(createErrorWithOffset(token.beginLine, token.beginColumn, message));
    }

    public void addAllErrors(ErrorCollector errorCollector) {
        errors.addAll(errorCollector.getErrorsRaw());
    }

    public void updateFilenames(Map<String, String> resourceHashes) {
        errors.forEach(error -> {
            String filenameWithoutExtension = FileUtils.getFilenameWithoutExtension(error.filename);
            String correctFilename = resourceHashes.get(filenameWithoutExtension);
            if (correctFilename != null) {
                error.setFilename(correctFilename);
            }
        });
    }

    private Collection<Error> getErrorsRaw() {
        return errors;
    }

    private Error createErrorWithOffset(int lineNumber, int columnNumber, String message) {
        if (lineNumber == 0) {
            return new Error(filename, lineOffset, columnOffset + columnNumber, message);
        }
        return new Error(filename, lineOffset + lineNumber, columnNumber, message);
    }
}
