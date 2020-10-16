package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.util.CustomModel;
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

import static com.graphhopper.routing.weighting.custom.ScriptWeighting.parseAndGuessParameters;

public abstract class PriorityScript implements EdgeToValueEntry {

    public PriorityScript() {
    }

    public void init(EncodedValueLookup lookup) {
    }

    public static EdgeToValueEntry create(CustomModel customModel, EncodedValueLookup lookup) {
        ScriptEvaluator se = new ScriptEvaluator();
        se.setClassName("Priority");
        se.setDefaultImports("static com.graphhopper.routing.ev.RoadClass.*");
        se.setOverrideMethod(new boolean[]{
                true,
        });
        se.setStaticMethod(new boolean[]{
                false,
        });
        se.setExtendedClass(PriorityScript.class);
        se.setReturnTypes(new Class[]{
                double.class,
        });
        se.setMethodNames(new String[]{
                "getValue",
        });
        se.setParameters(new String[][]{
                {"edge", "reverse"},
        }, new Class[][]{
                {EdgeIteratorState.class, boolean.class},
        });

        String mainUserExpression = "";
        boolean closedScript = false;
        HashSet<String> createObjects = new HashSet<>();
        ScriptWeighting.NameValidator nameValidator = name ->
                // allow all encoded values and constants
                lookup.hasEncodedValue(name) || name.toUpperCase(Locale.ROOT).equals(name);
        for (Map.Entry<String, Object> entry : customModel.getPriority().entrySet()) {
            if (!mainUserExpression.isEmpty())
                mainUserExpression += " : ";

            // TODO NOW value must be a number or a name -> no method and no boolean expression
            if (entry.getKey().equals(CustomWeighting.CATCH_ALL)) {
                if (!parseAndGuessParameters(createObjects, entry.getValue().toString(), nameValidator))
                    throw new IllegalArgumentException("Value not a valid, simple expression: " + entry.getValue().toString());
                mainUserExpression += entry.getValue();
                closedScript = true;
                break;
            } else {
                if (!parseAndGuessParameters(createObjects, entry.getKey(), nameValidator))
                    throw new IllegalArgumentException("Key not a valid, simple expression: " + entry.getKey());
                if (!parseAndGuessParameters(createObjects, entry.getValue().toString(), nameValidator))
                    throw new IllegalArgumentException("Value not a valid, simple expression: " + entry.getValue().toString());

                // TODO should we build the expressions via Java? new Java.ConditionalExpression(location, lhs, mhs, rhs);
                mainUserExpression += entry.getKey() + " ? " + entry.getValue();
            }
        }

        if (!closedScript)
            mainUserExpression += ": 1";

        try {
            String classSourceCode = "";
            String importSourceCode = "";
            String initSourceCode = "";
            List<Java.BlockStatement> statements = new ArrayList<>();
            for (String arg : createObjects) {
                if (lookup.hasEncodedValue(arg)) {
                    Parser parser = new Parser(new Scanner("parser1", new StringReader(
                            "Enum " + arg + " = reverse ? edge.getReverse(" + arg + "_enc) : edge.get(" + arg + "_enc);")));
                    statements.addAll(parser.parseBlockStatements());
                    String className = toClassName(arg);
                    String packageName = "com.graphhopper.routing.ev";
                    importSourceCode += "import static " + packageName + "." + className + ".*;\n";
                    importSourceCode += "import " + packageName + "." + className + ";\n";
                    classSourceCode += "protected EnumEncodedValue " + arg + "_enc;\n";
                    initSourceCode += "if (lookup.hasEncodedValue(" + className + ".KEY)) "
                            + arg + "_enc = lookup.getEnumEncodedValue(" + className + ".KEY, " + className + ".class);\n";
                }
            }

            final String classTemplate = ""
                    + "import " + PriorityScript.class.getName() + ";\n"
                    + "import " + EncodedValueLookup.class.getName() + ";\n"
                    + "import " + EnumEncodedValue.class.getName() + ";\n"
                    + "import " + EdgeIteratorState.class.getName() + ";\n"
                    + importSourceCode
                    + "\npublic class Test extends PriorityScript {\n"
                    + classSourceCode
                    + "   @Override "
                    + "   public void init(EncodedValueLookup lookup) {\n"
                    + initSourceCode
                    + "   }\n\n"
                    + "   @Override "
                    + "   public double getValue(EdgeIteratorState edge, boolean reverse) {\n"
                    + "      return 0.17; //will be overwritten by code injected in DeepCopier\n"
                    + "   }"
                    + "}";

            final String finalUserExpression = mainUserExpression;
            Java.AbstractCompilationUnit cu = new Parser(new Scanner("ignore", new StringReader(classTemplate))).
                    parseAbstractCompilationUnit();

            // instead of string appending safely add the expression via Java:
            cu = new DeepCopier() {

                @Override
                public Java.MethodDeclarator copyMethodDeclarator(Java.MethodDeclarator subject) throws CompileException {
                    if (!subject.name.equals("getValue"))
                        return super.copyMethodDeclarator(subject);

                    if (statements.isEmpty())
                        throw new IllegalStateException("Something went wrong");

                    try {
                        Parser parser = new Parser(new Scanner("parser2", new StringReader(finalUserExpression)));
                        Java.Rvalue rvalue = parser.parseConditionalExpression().toRvalueOrCompileException();
                        statements.add(new Java.ReturnStatement(new Location("ignore", 1, 1), rvalue));

                        Java.MethodDeclarator methodDecl = new Java.MethodDeclarator(
                                new Location("m1", 1, 1),
                                subject.getDocComment(),
                                this.copyModifiers(subject.getModifiers()),
                                this.copyOptionalTypeParameters(subject.typeParameters),
                                this.copyType(subject.type),
                                subject.name,
                                this.copyFormalParameters(subject.formalParameters),
                                this.copyTypes(subject.thrownExceptions),
                                this.copyOptionalElementValue(subject.defaultValue),
                                this.copyOptionalStatements(statements)
                        );
                        statements.forEach(st -> st.setEnclosingScope(methodDecl));
                        return methodDecl;
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }.copyAbstractCompilationUnit(cu);

            // would be nice to avoid that: https://github.com/janino-compiler/janino/issues/135
            StringWriter sw = new StringWriter();
            Unparser.unparse(cu, sw);

            SimpleCompiler sc = new SimpleCompiler();
            sc.cook(sw.toString());
            PriorityScript prio = (PriorityScript) sc.getClassLoader().
                    loadClass("Test").getDeclaredConstructor().newInstance();
            prio.init(lookup);
            return prio;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Problem with: " + mainUserExpression, ex);
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
