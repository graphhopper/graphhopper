package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.Location;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.*;
import org.codehaus.janino.util.DeepCopier;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

import static com.graphhopper.routing.weighting.custom.ScriptWeighting.parseAndGuessParametersFromCondition;

public class ScriptHelper {

    protected DecimalEncodedValue avg_speed_enc;

    public ScriptHelper() {
    }

    public void init(EncodedValueLookup lookup, DecimalEncodedValue avgSpeedEnc) {
        this.avg_speed_enc = avgSpeedEnc;
    }

    public double getPriority(EdgeIteratorState edge, boolean reverse) {
        return -1;
    }

    public double getSpeed(EdgeIteratorState edge, boolean reverse) {
        double speed = reverse ? edge.getReverse(avg_speed_enc) : edge.get(avg_speed_enc);
        if (Double.isInfinite(speed) || Double.isNaN(speed) || speed < 0)
            throw new IllegalStateException("Invalid estimated speed " + speed);
        return speed;
    }

    public static ScriptHelper create(CustomModel customModel, EncodedValueLookup lookup, double globalMaxSpeed, DecimalEncodedValue avgSpeedEnc) {
        try {
            //// for getPriority
            HashSet<String> priorityVariables = new HashSet<>();
            List<Java.BlockStatement> priorityStatements = new ArrayList<>();
            priorityStatements.addAll(verifyExpressions(priorityVariables, customModel.getPriority(), lookup,
                    "return (", "return 1;"));
            String priorityStartBlock = "";
            for (String arg : priorityVariables) {
                priorityStartBlock += "Enum " + arg + " = reverse ? edge.getReverse(" + arg + "_enc) : edge.get(" + arg + "_enc);";
            }
            priorityStatements.addAll(0, new Parser(new Scanner("parser1", new StringReader(priorityStartBlock))).parseBlockStatements());

            //// for getSpeed
            HashSet<String> speedVariables = new HashSet<>();
            List<Java.BlockStatement> speedStatements = new ArrayList<>();
            speedStatements.addAll(verifyExpressions(speedVariables, customModel.getSpeedFactor(), lookup,
                    "speed *= (", ""));
            speedStatements.addAll(verifyExpressions(speedVariables, customModel.getMaxSpeed(), lookup,
                    "speed = Math.min(speed,", "return Math.min(speed, " + globalMaxSpeed + ");"));
            String speedStartBlock = "double speed = reverse ? edge.getReverse(avg_speed_enc) : edge.get(avg_speed_enc);\n"
                    + "if (Double.isInfinite(speed) || Double.isNaN(speed) || speed < 0) throw new IllegalStateException(\"Invalid estimated speed \" + speed);\n";
            // a bit inefficient to possibly define variables twice, but for now we have two separate methods
            for (String arg : speedVariables) {
                speedStartBlock += "Enum " + arg + " = reverse ? edge.getReverse(" + arg + "_enc) : edge.get(" + arg + "_enc);\n";
            }
            speedStatements.addAll(0, new Parser(new Scanner("parser2", new StringReader(speedStartBlock))).parseBlockStatements());

            //// add the parsed expressions (now BlockStatement) via DeepCopier:
            String classTemplate = createClassTemplate(priorityVariables, speedVariables, lookup);
            Java.AbstractCompilationUnit cu = new Parser(new Scanner("ignore", new StringReader(classTemplate))).
                    parseAbstractCompilationUnit();
            cu = new DeepCopier() {

                @Override
                public Java.MethodDeclarator copyMethodDeclarator(Java.MethodDeclarator subject) throws CompileException {
                    if (subject.name.equals("getSpeed") && !speedStatements.isEmpty()) {
                        return copyMethod(subject, this, speedStatements);
                    } else if (subject.name.equals("getPriority")) {
                        return copyMethod(subject, this, priorityStatements);
                    } else {
                        return super.copyMethodDeclarator(subject);
                    }
                }
            }.copyAbstractCompilationUnit(cu);

            // TODO avoid that: https://github.com/janino-compiler/janino/issues/135
            StringWriter sw = new StringWriter();
            Unparser.unparse(cu, sw);

            SimpleCompiler sc = new SimpleCompiler();
            sc.cook(sw.toString());
            ScriptHelper prio = (ScriptHelper) sc.getClassLoader().
                    loadClass("Test").getDeclaredConstructor().newInstance();
            prio.init(lookup, avgSpeedEnc);
            return prio;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Problem with: " + customModel.getPriority().toString(), ex);
        }
    }

    /**
     * Create the class source file from the detected variables with proper imports and declarations if EncodedValue
     */
    private static String createClassTemplate(Set<String> priorityVariables, Set<String> speedVariables, EncodedValueLookup lookup) {
        final StringBuilder importSourceCode = new StringBuilder();
        final StringBuilder classSourceCode = new StringBuilder();
        final StringBuilder initSourceCode = new StringBuilder("this.avg_speed_enc = avgSpeedEnc;\n");
        Set<String> set = new HashSet<>(priorityVariables);
        set.addAll(speedVariables);
        for (String arg : set) {
            if (lookup.hasEncodedValue(arg)) {
                EncodedValue enc = lookup.getEncodedValue(arg, EncodedValue.class);
                if (!EncodingManager.isSharedEV(enc))
                    continue;
                String className = toClassName(arg);
                String packageName = "com.graphhopper.routing.ev";
                importSourceCode.append("import static " + packageName + "." + className + ".*;\n");
                importSourceCode.append("import " + packageName + "." + className + ";\n");
                classSourceCode.append("protected " + enc.getClass().getName() + " " + arg + "_enc;\n");
                initSourceCode.append("if (lookup.hasEncodedValue(" + className + ".KEY)) ");
                initSourceCode.append(arg + "_enc = lookup.get" + enc.getClass().getSimpleName() + "(" + className + ".KEY, " + className + ".class);\n");
            }
        }

        return ""
                + "import " + ScriptHelper.class.getName() + ";\n"
                + "import " + EncodedValueLookup.class.getName() + ";\n"
                + "import " + EdgeIteratorState.class.getName() + ";\n"
                + importSourceCode
                + "\npublic class Test extends ScriptHelper {\n"
                + classSourceCode
                + "   @Override\n"
                + "   public void init(EncodedValueLookup lookup, " + DecimalEncodedValue.class.getName() + " avgSpeedEnc) {\n"
                + initSourceCode
                + "   }\n\n"
                + "   @Override\n"
                + "   public double getPriority(EdgeIteratorState edge, boolean reverse) {\n"
                + "      return 1; //will be overwritten by code injected in DeepCopier\n"
                + "   }\n"
                + "   @Override\n"
                + "   public double getSpeed(EdgeIteratorState edge, boolean reverse) {\n"
                + "      return 0; //will be overwritten by code injected in DeepCopier\n"
                + "   }\n"
                + "}";
    }

    /**
     * This method does:
     * 1. check user expressions via parseConditionalExpression. It has a white list of variables and methods.
     * 2. while this check it also guesses the variable names and stores it in createObjects
     * 3. creates if-then-elseif expressions
     *
     * @return the created if-then-elseif expressions
     */
    private static List<Java.BlockStatement> verifyExpressions(Set<String> createObjects,
                                                               Map<String, Object> map, EncodedValueLookup lookup,
                                                               String function, String lastStmt) throws Exception {

        Set<String> allowedNames = new HashSet<>(Arrays.asList("edge", "Math"));
        ScriptWeighting.NameValidator nameInConditionValidator = name ->
                // allow all encoded values and constants
                lookup.hasEncodedValue(name) || name.toUpperCase(Locale.ROOT).equals(name) || allowedNames.contains(name);

        StringBuilder expressions = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String expression = entry.getKey();
            if (expression.equals(CustomWeighting.CATCH_ALL))
                throw new IllegalArgumentException("replace all '*' expressions with 'true'");
            if (!parseAndGuessParametersFromCondition(createObjects, expression, nameInConditionValidator))
                throw new IllegalArgumentException("Key is invalid simple condition: " + expression);
            Object numberObj = entry.getValue();
            if (!(numberObj instanceof Number))
                throw new IllegalArgumentException("value not a Number " + numberObj);
            Number number = (Number) numberObj;

            if (count > 0)
                expressions.append("else ");
            expressions.append("if (" + expression + ") " + function + " " + number + " );\n");
            count++;
        }
        expressions.append(lastStmt + "\n");
        // TODO can we reuse Java.Atom created in parseAndGuessParametersFromCondition?
        return new Parser(new Scanner("priority_parser", new StringReader(expressions.toString()))).
                parseBlockStatements();
    }

    private static Java.MethodDeclarator copyMethod(Java.MethodDeclarator subject, DeepCopier deepCopier,
                                                    List<Java.BlockStatement> statements) {
        try {
            if (statements.isEmpty())
                throw new IllegalArgumentException("Statements cannot be empty when copying method");
            Java.MethodDeclarator methodDecl = new Java.MethodDeclarator(
                    new Location("m1", 1, 1),
                    subject.getDocComment(),
                    deepCopier.copyModifiers(subject.getModifiers()),
                    deepCopier.copyOptionalTypeParameters(subject.typeParameters),
                    deepCopier.copyType(subject.type),
                    subject.name,
                    deepCopier.copyFormalParameters(subject.formalParameters),
                    deepCopier.copyTypes(subject.thrownExceptions),
                    deepCopier.copyOptionalElementValue(subject.defaultValue),
                    deepCopier.copyOptionalStatements(statements)
            );
            statements.forEach(st -> st.setEnclosingScope(methodDecl));
            statements.clear();
            return methodDecl;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static String toClassName(String arg) {
        if (arg.isEmpty())
            return "";
        if (arg.length() == 1)
            return "" + Character.toLowerCase(arg.charAt(0));
        String clazz = Helper.underScoreToCamelCase(arg);
        return Character.toUpperCase(clazz.charAt(0)) + clazz.substring(1);
    }
}
