package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.*;
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
            // TODO create expressions via new Java.ConditionalExpression(location, lhs, mhs, rhs) (reuse objects created in the parse methods before)
            HashSet<String> priorityVariables = new HashSet<>();
            List<Java.BlockStatement> priorityStatements = new ArrayList<>();
            addStatementsAndGuessVariables(priorityVariables, priorityStatements, customModel.getPriority(), lookup,
                    "return (", "return 1;");
            // a bit inefficient to define some variables twice but we have two methods for now
            for (String arg : priorityVariables) {
                Parser parser = new Parser(new Scanner("parser1", new StringReader(
                        "Enum " + arg + " = reverse ? edge.getReverse(" + arg + "_enc) : edge.get(" + arg + "_enc);")));
                priorityStatements.addAll(0, parser.parseBlockStatements());
            }

            HashSet<String> speedVariables = new HashSet<>();
            List<Java.BlockStatement> speedStatements = new ArrayList<>();
            addStatementsAndGuessVariables(speedVariables, speedStatements, customModel.getSpeedFactor(), lookup,
                    "speed *= (", "");
            addStatementsAndGuessVariables(speedVariables, speedStatements, customModel.getMaxSpeed(), lookup,
                    "speed = Math.min(speed,", "return Math.min(speed, " + globalMaxSpeed + ");");
            String speedStartExpressions = "double speed = reverse ? edge.getReverse(avg_speed_enc) : edge.get(avg_speed_enc);\n"
                    + "if (Double.isInfinite(speed) || Double.isNaN(speed) || speed < 0) throw new IllegalStateException(\"Invalid estimated speed \" + speed);\n";
            for (String arg : speedVariables) {
                speedStartExpressions += "Enum " + arg + " = reverse ? edge.getReverse(" + arg + "_enc) : edge.get(" + arg + "_enc);\n";
            }
            speedStatements.addAll(0, new Parser(new Scanner("parser2", new StringReader(speedStartExpressions))).parseBlockStatements());

            final StringBuilder importSourceCode = new StringBuilder();
            final StringBuilder classSourceCode = new StringBuilder();
            final StringBuilder initSourceCode = new StringBuilder("this.avg_speed_enc = avgSpeedEnc;\n");
            Set<String> set = new HashSet<>(priorityVariables);
            set.addAll(speedVariables);
            for (String arg : set) {
                if (lookup.hasEncodedValue(arg)) {
                    EncodedValue enc = lookup.getEncodedValue(arg, EncodedValue.class);
                    if (!EncodingManager.isSharedEV(enc))
                        break;

                    String className = toClassName(arg);
                    String packageName = "com.graphhopper.routing.ev";
                    importSourceCode.append("import static " + packageName + "." + className + ".*;\n");
                    importSourceCode.append("import " + packageName + "." + className + ";\n");
                    String evType = enc.getClass().getSimpleName();
                    classSourceCode.append("protected " + evType + " " + arg + "_enc;\n");
                    initSourceCode.append("if (lookup.hasEncodedValue(" + className + ".KEY)) ");
                    initSourceCode.append(arg + "_enc = lookup.get" + evType + "(" + className + ".KEY, " + className + ".class);\n");
                }
            }

            final String classTemplate = ""
                    + "import " + ScriptHelper.class.getName() + ";\n"
                    + "import " + EncodedValueLookup.class.getName() + ";\n"
                    + "import " + EnumEncodedValue.class.getName() + ";\n"
                    + "import " + DecimalEncodedValue.class.getName() + ";\n"
                    + "import " + IntEncodedValue.class.getName() + ";\n"
                    + "import " + EdgeIteratorState.class.getName() + ";\n"
                    + importSourceCode
                    + "\npublic class Test extends ScriptHelper {\n"
                    + classSourceCode
                    + "   @Override\n"
                    + "   public void init(EncodedValueLookup lookup, DecimalEncodedValue avgSpeedEnc) {\n"
                    + initSourceCode
                    + "   }\n\n"
                    + "   @Override\n"
                    + "   public double getPriority(EdgeIteratorState edge, boolean reverse) {\n"
                    + "      return 1; //will be overwritten by code injected in DeepCopier\n"
                    + "   }\n"
                    + "   @Override\n"
                    + "   public double getSpeed(EdgeIteratorState edge, boolean reverse) {\n"
                    + "      return speed; //will be overwritten by code injected in DeepCopier\n"
                    + "   }\n"
                    + "}";

            Java.AbstractCompilationUnit cu = new Parser(new Scanner("ignore", new StringReader(classTemplate))).
                    parseAbstractCompilationUnit();

            // instead of string appending we safely add the expression via Java and compile before:
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

            // would be nice to avoid that: https://github.com/janino-compiler/janino/issues/135
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

    private static void addStatementsAndGuessVariables(Set<String> createObjects, List<Java.BlockStatement> createStatements,
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

        Parser parser = new Parser(new Scanner("priority_parser", new StringReader(expressions.toString())));
        createStatements.addAll(parser.parseBlockStatements());
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
