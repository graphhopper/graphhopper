package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.DefaultEncodedValueFactory;
import org.codehaus.janino.*;

import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Expression visitor for right-hand side of e.g. limit_to and multiply_by
 */
public class ValueExpressionVisitor implements Visitor.AtomVisitor<Boolean, Exception> {

    private final ParseResult result;
    private final NameValidator nameValidator;
    private final Set<String> allowedMethods = new HashSet<>(Arrays.asList("sqrt", "abs"));
    private String invalidMessage;
    private DefaultEncodedValueFactory factory = new DefaultEncodedValueFactory();

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
                try {
                    factory.create(arg);
                    invalidMessage = "encoded value '" + arg + "' not available";
                    return false;
                } catch (Exception ex) {
                }
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
            invalidMessage = mi.methodName + " is illegal method";
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
}
