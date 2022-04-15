package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.IntEncodedValue;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.*;

import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Expression visitor for right-hand side of e.g. limit_to and multiply_by
 */
public class ValueExpressionVisitor implements Visitor.AtomVisitor<Boolean, Exception> {

    private final ParseResult result;
    private final NameValidator nameValidator;
    private final Set<String> allowedMethods = new HashSet<>(Arrays.asList("sqrt", "abs"));
    private String invalidMessage;

    public ValueExpressionVisitor(ParseResult result, NameValidator nameValidator) {
        this.result = result;
        this.nameValidator = nameValidator;
    }

    // allow only methods and other identifiers (constants and encoded values)
    boolean isValidIdentifier(String identifier) {
        if (nameValidator.isValid(identifier)) {
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
                // skip methods like this.in() for now
                if (mi.target != null) {
                    // edge.getDistance, Math.sqrt => check target name (edge or Math)
                    Java.AmbiguousName n = (Java.AmbiguousName) mi.target.toRvalue();
                    if (n.identifiers.length == 2 && isValidIdentifier(n.identifiers[0])) return true;
                }
            }
            invalidMessage = mi.methodName + " is an illegal method";
            return false;
        } else if (rv instanceof Java.ParenthesizedExpression) {
            return ((Java.ParenthesizedExpression) rv).value.accept(this);
        } else if (rv instanceof Java.BinaryOperation) {
            Java.BinaryOperation binOp = (Java.BinaryOperation) rv;
            String op = binOp.operator;
            result.operators.add(op);
            if (op.equals("*") || op.equals("/") || op.equals("+") || binOp.operator.equals("-")) {
                return binOp.lhs.accept(this) && binOp.rhs.accept(this);
            }
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

    static ParseResult parse(String expression, NameValidator validator) {
        ParseResult result = new ParseResult();
        try {
            Parser parser = new Parser(new Scanner("ignore", new StringReader(expression)));
            Java.Atom atom = parser.parseConditionalExpression();
            if (parser.peek().type == TokenType.END_OF_INPUT) {
                result.guessedVariables = new LinkedHashSet<>();
                result.operators = new LinkedHashSet<>();
                ValueExpressionVisitor visitor = new ValueExpressionVisitor(result, validator);
                result.ok = atom.accept(visitor);
                result.invalidMessage = visitor.invalidMessage;
            }
        } catch (Exception ex) {
        }
        return result;
    }

    static double[] findMinMax(Set<String> createdObjects, String valueExpression, EncodedValueLookup lookup) {
        ParseResult result = parse(valueExpression, lookup::hasEncodedValue);
        if (!result.ok)
            throw new IllegalArgumentException(result.invalidMessage);
        if ((result.operators.contains("-") || result.operators.contains("/")) && result.guessedVariables.size() > 1)
            throw new IllegalArgumentException("Currently only a single EncodedValue in the value expression is allowed when expression contains \"/\" or \"-\". " + valueExpression);

        try {
            // Speed optimization for numbers only as its over 200x faster than ExpressionEvaluator+cook+evaluate!
            // We still call the parse() method before as it is only ~3x slower and might increase security slightly. Because certain
            // expressions are accepted from Double.parseDouble but parse() rejects them. With this call order we avoid unexpected security problems.
            double val = Double.parseDouble(valueExpression);
            return new double[]{val, val};
        } catch (NumberFormatException ex) {
        }

        try {
            if (result.guessedVariables.isEmpty()) { // expressions without encoded values
                ExpressionEvaluator ee = new ExpressionEvaluator();
                ee.cook(valueExpression);
                double val = ((Number) ee.evaluate()).doubleValue();
                return new double[]{val, val};
            }

            createdObjects.addAll(result.guessedVariables);
            ExpressionEvaluator ee = new ExpressionEvaluator();
            List<String> names = new ArrayList<>(result.guessedVariables.size());
            List<Class<?>> values = new ArrayList<>(result.guessedVariables.size());
            for (String var : result.guessedVariables) {
                names.add(var);
                values.add(double.class);
            }
            ee.setParameters(names.toArray(new String[0]), values.toArray(new Class[0]));
            ee.cook(valueExpression);
            List<Object> args = new ArrayList<>();
            for (String var : result.guessedVariables) {
                EncodedValue enc = lookup.getEncodedValue(var, EncodedValue.class);
                if (enc instanceof DecimalEncodedValue)
                    args.add(((DecimalEncodedValue) enc).getMaxDecimal());
                else if (enc instanceof IntEncodedValue)
                    args.add(((IntEncodedValue) enc).getMaxInt());
                else
                    throw new IllegalArgumentException("Cannot use non-number data in value expression");
            }
            Number val1 = (Number) ee.evaluate(args.toArray());

            args.clear();
            for (String var : result.guessedVariables) {
                EncodedValue enc = lookup.getEncodedValue(var, EncodedValue.class);
                if (enc instanceof DecimalEncodedValue)
                    args.add(((DecimalEncodedValue) enc).getMinDecimal());
                else if (enc instanceof IntEncodedValue)
                    args.add(((IntEncodedValue) enc).getMinInt());
                else
                    throw new IllegalArgumentException("Cannot use non-number data in value expression");
            }
            Number val2 = (Number) ee.evaluate(args.toArray());
            return new double[]{Math.min(val1.doubleValue(), val2.doubleValue()), Math.max(val1.doubleValue(), val2.doubleValue())};
        } catch (CompileException | InvocationTargetException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
}
