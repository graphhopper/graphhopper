package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.MinMax;
import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.IntEncodedValue;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.*;
import org.codehaus.janino.Scanner;

import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import static com.graphhopper.json.Statement.Keyword.IF;

/**
 * Expression visitor for right-hand side value of limit_to or multiply_by.
 */
public class ValueExpressionVisitor implements Visitor.AtomVisitor<Boolean, Exception> {

    private static final Set<String> allowedMethodParents = new HashSet<>(Arrays.asList("Math"));
    private static final Set<String> allowedMethods = new HashSet<>(Arrays.asList("sqrt"));
    private final ParseResult result;
    private final NameValidator variableValidator;
    private String invalidMessage;

    public ValueExpressionVisitor(ParseResult result, NameValidator variableValidator) {
        this.result = result;
        this.variableValidator = variableValidator;
    }

    // allow only methods and other identifiers (constants and encoded values)
    boolean isValidIdentifier(String identifier) {
        if (variableValidator.isValid(identifier)) {
            if (!Character.isUpperCase(identifier.charAt(0)))
                result.guessedVariables.add(identifier);
            return true;
        }
        return false;
    }

    @Override
    public Boolean visitRvalue(Java.Rvalue rv) throws Exception {
        if (rv instanceof Java.AmbiguousName) {
            Java.AmbiguousName n = (Java.AmbiguousName) rv;
            if (n.identifiers.length == 1) {
                String arg = n.identifiers[0];
                // e.g. like road_class
                if (isValidIdentifier(arg)) return true;
                invalidMessage = "'" + arg + "' not available";
                return false;
            }
            invalidMessage = "identifier " + n + " invalid";
            return false;
        }
        if (rv instanceof Java.Literal) {
            return true;
        } else if (rv instanceof Java.UnaryOperation) {
            Java.UnaryOperation uop = (Java.UnaryOperation) rv;
            result.operators.add(uop.operator);
            if (uop.operator.equals("-"))
                return uop.operand.accept(this);
            return false;
        } else if (rv instanceof Java.MethodInvocation) {
            Java.MethodInvocation mi = (Java.MethodInvocation) rv;
            if (allowedMethods.contains(mi.methodName)) {
                // skip methods like this.in()
                if (mi.target != null) {
                    // edge.getDistance(), Math.sqrt(2) => check target name (edge or Math)
                    Java.AmbiguousName n = (Java.AmbiguousName) mi.target.toRvalue();
                    if (n.identifiers.length == 2) {
                        if (allowedMethodParents.contains(n.identifiers[0])) {
                            // edge.getDistance(), Math.sqrt(x) => check target name i.e. edge or Math
                            if (mi.arguments.length == 0) {
                                result.guessedVariables.add(n.identifiers[0]); // return "edge"
                                return true;
                            } else if (mi.arguments.length == 1) {
                                // return "x" but verify before
                                return mi.arguments[0].accept(this);
                            }
                        }
                        // TODO unlike in ConditionalExpressionVisitor we don't support a call like road_class.ordinal()
                        //  as this is currently unsupported in FindMinMax
                    }
                }
            }
            invalidMessage = mi.methodName + " is an illegal method in a value expression";
            return false;
        } else if (rv instanceof Java.ParenthesizedExpression) {
            return ((Java.ParenthesizedExpression) rv).value.accept(this);
        } else if (rv instanceof Java.BinaryOperation) {
            Java.BinaryOperation binOp = (Java.BinaryOperation) rv;
            String op = binOp.operator;
            result.operators.add(op);
            if (op.equals("*") || op.equals("+") || binOp.operator.equals("-")) {
                return binOp.lhs.accept(this) && binOp.rhs.accept(this);
            }
            invalidMessage = "invalid operation '" + op + "'";
            return false;
        }
        return false;
    }

    @Override
    public Boolean visitPackage(Java.Package p) {
        return false;
    }

    @Override
    public Boolean visitType(Java.Type t) {
        return false;
    }

    @Override
    public Boolean visitConstructorInvocation(Java.ConstructorInvocation ci) {
        return false;
    }

    static ParseResult parse(String expression, NameValidator variableValidator) {
        ParseResult result = new ParseResult();
        try {
            Parser parser = new Parser(new Scanner("ignore", new StringReader(expression)));
            Java.Atom atom = parser.parseConditionalExpression();
            if (parser.peek().type == TokenType.END_OF_INPUT) {
                result.guessedVariables = new LinkedHashSet<>();
                result.operators = new LinkedHashSet<>();
                ValueExpressionVisitor visitor = new ValueExpressionVisitor(result, variableValidator);
                result.ok = atom.accept(visitor);
                result.invalidMessage = visitor.invalidMessage;
            }
        } catch (Exception ex) {
        }
        return result;
    }

    static Set<String> findVariables(List<Statement> statements, EncodedValueLookup lookup) {
        List<List<Statement>> blocks = CustomModelParser.splitIntoBlocks(statements);
        Set<String> variables = new LinkedHashSet<>();
        for (List<Statement> block : blocks) ValueExpressionVisitor.findVariablesForBlock(variables, block, lookup);
        return variables;
    }

    private static void findVariablesForBlock(Set<String> createdObjects, List<Statement> block, EncodedValueLookup lookup) {
        if (block.isEmpty() || !IF.equals(block.get(0).getKeyword()))
            throw new IllegalArgumentException("Every block must start with an if-statement");

        if (block.get(0).getCondition().trim().equals("true")) {
            createdObjects.addAll(ValueExpressionVisitor.findVariables(block.get(0).getValue(), lookup));
        } else {
            for (Statement s : block) {
                createdObjects.addAll(ValueExpressionVisitor.findVariables(s.getValue(), lookup));
            }
        }
    }

    static Set<String> findVariables(String valueExpression, EncodedValueLookup lookup) {
        ParseResult result = parse(valueExpression, lookup::hasEncodedValue);
        if (!result.ok)
            throw new IllegalArgumentException(result.invalidMessage);
        if (result.guessedVariables.size() > 1)
            throw new IllegalArgumentException("Currently only a single EncodedValue is allowed on the right-hand side, but was " + result.guessedVariables.size() + ". Value expression: " + valueExpression);

        // TODO Nearly duplicate code as in findMinMax
        double value;
        try {
            // Speed optimization for numbers only as its over 200x faster than ExpressionEvaluator+cook+evaluate!
            // We still call the parse() method before as it is only ~3x slower and might increase security slightly. Because certain
            // expressions are accepted from Double.parseDouble but parse() rejects them. With this call order we avoid unexpected security problems.
            value = Double.parseDouble(valueExpression);
        } catch (NumberFormatException ex) {
            try {
                if (result.guessedVariables.isEmpty()) { // without encoded values
                    ExpressionEvaluator ee = new ExpressionEvaluator();
                    ee.cook(valueExpression);
                    value = ((Number) ee.evaluate()).doubleValue();
                } else if (lookup.hasEncodedValue(valueExpression)) { // speed up for common case that complete right-hand side is the encoded value
                    EncodedValue enc = lookup.getEncodedValue(valueExpression, EncodedValue.class);
                    value = Math.min(getMin(enc), getMax(enc));
                } else {
                    // single encoded value
                    ExpressionEvaluator ee = new ExpressionEvaluator();
                    String var = result.guessedVariables.iterator().next();
                    ee.setParameters(new String[]{var}, new Class[]{double.class});
                    ee.cook(valueExpression);
                    double max = getMax(lookup.getEncodedValue(var, EncodedValue.class));
                    Number val1 = (Number) ee.evaluate(max);
                    double min = getMin(lookup.getEncodedValue(var, EncodedValue.class));
                    Number val2 = (Number) ee.evaluate(min);
                    value = Math.min(val1.doubleValue(), val2.doubleValue());
                }
            } catch (CompileException | InvocationTargetException ex2) {
                throw new IllegalArgumentException(ex2);
            }
        }
        if (value < 0)
            throw new IllegalArgumentException("illegal expression as it can result in a negative weight: " + valueExpression);

        return result.guessedVariables;
    }

    static MinMax findMinMax(String valueExpression, EncodedValueLookup lookup) {
        ParseResult result = parse(valueExpression, lookup::hasEncodedValue);
        if (!result.ok)
            throw new IllegalArgumentException(result.invalidMessage);
        if (result.guessedVariables.size() > 1)
            throw new IllegalArgumentException("Currently only a single EncodedValue is allowed on the right-hand side, but was " + result.guessedVariables.size() + ". Value expression: " + valueExpression);

        // TODO Nearly duplicate as in findVariables
        try {
            // Speed optimization for numbers only as its over 200x faster than ExpressionEvaluator+cook+evaluate!
            // We still call the parse() method before as it is only ~3x slower and might increase security slightly. Because certain
            // expressions are accepted from Double.parseDouble but parse() rejects them. With this call order we avoid unexpected security problems.
            double val = Double.parseDouble(valueExpression);
            return new MinMax(val, val);
        } catch (NumberFormatException ex) {
        }

        try {
            if (result.guessedVariables.isEmpty()) { // without encoded values
                ExpressionEvaluator ee = new ExpressionEvaluator();
                ee.cook(valueExpression);
                double val = ((Number) ee.evaluate()).doubleValue();
                return new MinMax(val, val);
            }

            if (lookup.hasEncodedValue(valueExpression)) { // speed up for common case that complete right-hand side is the encoded value
                EncodedValue enc = lookup.getEncodedValue(valueExpression, EncodedValue.class);
                double min = getMin(enc), max = getMax(enc);
                return new MinMax(min, max);
            }

            ExpressionEvaluator ee = new ExpressionEvaluator();
            String var = result.guessedVariables.iterator().next();
            ee.setParameters(new String[]{var}, new Class[]{double.class});
            ee.cook(valueExpression);
            double max = getMax(lookup.getEncodedValue(var, EncodedValue.class));
            Number val1 = (Number) ee.evaluate(max);
            double min = getMin(lookup.getEncodedValue(var, EncodedValue.class));
            Number val2 = (Number) ee.evaluate(min);
            return new MinMax(Math.min(val1.doubleValue(), val2.doubleValue()), Math.max(val1.doubleValue(), val2.doubleValue()));
        } catch (CompileException | InvocationTargetException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    static double getMin(EncodedValue enc) {
        if (enc instanceof DecimalEncodedValue)
            return ((DecimalEncodedValue) enc).getMinStorableDecimal();
        else if (enc instanceof IntEncodedValue) return ((IntEncodedValue) enc).getMinStorableInt();
        throw new IllegalArgumentException("Cannot use non-number data '" + enc.getName() + "' in value expression");
    }

    static double getMax(EncodedValue enc) {
        if (enc instanceof DecimalEncodedValue)
            return ((DecimalEncodedValue) enc).getMaxOrMaxStorableDecimal();
        else if (enc instanceof IntEncodedValue)
            return ((IntEncodedValue) enc).getMaxOrMaxStorableInt();
        throw new IllegalArgumentException("Cannot use non-number data '" + enc.getName() + "' in value expression");
    }
}
