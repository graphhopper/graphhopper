package com.graphhopper.expression;

public class GSParseException extends RuntimeException {

    String line;
    int lineNumber;

    public GSParseException(String message, String line, int lineNumber) {
        super(message);
        this.line = line;
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getLine() {
        return line;
    }
}
