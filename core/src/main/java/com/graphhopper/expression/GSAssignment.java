package com.graphhopper.expression;

import java.util.ArrayList;
import java.util.List;

public class GSAssignment {
    private final List<GSExpression> expressions = new ArrayList<>();
    private final String name;
    private final int lineNumber;

    public GSAssignment(String name, int lineNumber) {
        this.name = name;
        this.lineNumber = lineNumber;
    }

    public void add(GSExpression expression) {
        expressions.add(expression);
    }

    public String getName() {
        return name;
    }

    public List<GSExpression> getExpressions() {
        return expressions;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(lineNumber);
        sb.append("\t");
        sb.append(name);
        sb.append("\n");
        for (GSExpression expression : expressions) {
            sb.append(expression.getLineNumber());
            sb.append("\t");
            sb.append("\t");
            sb.append(expression);
            sb.append("\n");
        }
        return sb.toString();
    }
}
