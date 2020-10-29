package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.RouteNetwork;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.Polygon;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.Location;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.*;
import org.codehaus.janino.util.DeepCopier;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

import static com.graphhopper.routing.weighting.custom.CustomWeighting.FIRST_MATCH;

public class ScriptHelper {
    static final String AREA_PREFIX = "in_area_";
    protected DecimalEncodedValue avg_speed_enc;

    public ScriptHelper() {
    }

    public void init(EncodedValueLookup lookup, DecimalEncodedValue avgSpeedEnc, Map<String, JsonFeature> areas) {
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

    public static boolean in(Polygon p, EdgeIteratorState edge) {
        BBox bbox = GHUtility.createBBox(edge);
        if (p.getBounds().intersects(bbox))
            return p.intersects(edge.fetchWayGeometry(FetchMode.ALL).makeImmutable()); // TODO PERF: cache bbox and edge wayGeometry for multiple area
        return false;
    }

    public static ScriptHelper create(CustomModel customModel, EncodedValueLookup lookup,
                                      double globalMaxSpeed, double maxSpeedFallback, DecimalEncodedValue avgSpeedEnc) {
        Java.AbstractCompilationUnit cu = null;
        try {
            //// for getPriority
            HashSet<String> priorityVariables = new LinkedHashSet<>();
            List<Java.BlockStatement> priorityStatements = new ArrayList<>();
            priorityStatements.addAll(verifyExpressions(new StringBuilder("double value = 1;"), "priority_user_statements",
                    priorityVariables, customModel.getPriority(), lookup,
                    n -> "value *= " + n + ";\n", "return value;"));
            String priorityMethodStartBlock = "";
            for (String arg : priorityVariables) {
                priorityMethodStartBlock += getVariableDeclaration(lookup, arg);
            }
            priorityStatements.addAll(0, new Parser(new Scanner("getPriority", new StringReader(priorityMethodStartBlock))).
                    parseBlockStatements());

            //// for getSpeed
            HashSet<String> speedVariables = new LinkedHashSet<>();
            List<Java.BlockStatement> speedStatements = new ArrayList<>();
            speedStatements.addAll(verifyExpressions(new StringBuilder(), "speed_factor_user_statements", speedVariables, customModel.getSpeedFactor(), lookup,
                    n -> "speed *= " + n + ";\n", ""));
            StringBuilder codeSB = new StringBuilder("boolean applied = false;\n");
            speedStatements.addAll(verifyExpressions(codeSB, "max_speed_user_statements",
                    speedVariables, customModel.getMaxSpeed(), lookup,
                    n -> "applied = true; speed = Math.min(speed," + n + ");",
                    "if (!applied && speed > " + maxSpeedFallback + ") return " + maxSpeedFallback + ";\n" +
                            "return Math.min(speed, " + globalMaxSpeed + ");\n"));
            String speedMethodStartBlock = "double speed = reverse ? edge.getReverse(avg_speed_enc) : edge.get(avg_speed_enc);\n"
                    + "if (Double.isInfinite(speed) || Double.isNaN(speed) || speed < 0)"
                    + " throw new IllegalStateException(\"Invalid estimated speed \" + speed);\n";
            // a bit inefficient to possibly define variables twice, but for now we have two separate methods
            for (String arg : speedVariables) {
                speedMethodStartBlock += getVariableDeclaration(lookup, arg);
            }
            speedStatements.addAll(0, new Parser(new Scanner("getSpeed", new StringReader(speedMethodStartBlock))).
                    parseBlockStatements());

            //// add the parsed expressions (converted to BlockStatement) via DeepCopier:
            String classTemplate = createClassTemplate(priorityVariables, speedVariables, lookup);
            cu = new Parser(new Scanner("source", new StringReader(classTemplate))).
                    parseAbstractCompilationUnit();
            cu = new DeepCopier() {
                @Override
                public Java.FieldDeclaration copyFieldDeclaration(Java.FieldDeclaration subject) throws CompileException {
                    // for https://github.com/janino-compiler/janino/issues/135
                    Java.FieldDeclaration fd = super.copyFieldDeclaration(subject);
                    fd.setEnclosingScope(subject.getEnclosingScope());
                    return fd;
                }

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

            // mydebug(cu);
            SimpleCompiler sc = new SimpleCompiler();
            sc.cook(cu);
            ScriptHelper prio = (ScriptHelper) sc.getClassLoader().
                    loadClass("Test").getDeclaredConstructor().newInstance();
            customModel.getAreas().keySet().stream().forEach(areaId -> {
                if (areaId.length() > 20) throw new IllegalArgumentException("Area id too long: " + areaId.length());
            });
            prio.init(lookup, avgSpeedEnc, customModel.getAreas());
            return prio;
        } catch (Exception ex) {
            mydebug(cu);
            String location = "";
            if (ex instanceof CompileException)
                location = " in " + ((CompileException) ex).getLocation().getFileName();
            throw new IllegalArgumentException("Problem" + location + " with: " + customModel, ex);
        }
    }

    private static void mydebug(Java.AbstractCompilationUnit cu) {
        if (cu != null) {
            StringWriter sw = new StringWriter();
            Unparser.unparse(cu, sw);
            System.out.println(sw.toString());
        }
    }

    private static final Set<String> allowedNames = new HashSet<>(Arrays.asList("edge", "Math"));

    private static boolean isValidVariableName(String name) {
        return name.startsWith(AREA_PREFIX) || allowedNames.contains(name);
    }

    private static String getVariableDeclaration(EncodedValueLookup lookup, String arg) {
        if (arg.startsWith(AREA_PREFIX)) {
            String id = arg.substring(AREA_PREFIX.length());
            return "boolean " + arg + " = in(area_" + id + ", edge);\n";
        } else if (lookup.hasEncodedValue(arg)) {
            EncodedValue enc = lookup.getEncodedValue(arg, EncodedValue.class);
            return getPrimitive(enc.getClass()) + " " + arg + " = reverse ? " +
                    "edge.getReverse((" + getInterface(enc) + ")" + arg + "_enc) : " +
                    "edge.get((" + getInterface(enc) + ")" + arg + "_enc);\n";
        } else if (isValidVariableName(arg)) {
            return "";
        } else {
            throw new IllegalArgumentException("Not supported " + arg);
        }
    }

    static String getInterface(EncodedValue enc) {
        if (enc.getClass().getInterfaces().length == 0)
            return enc.getClass().getSimpleName();
        return enc.getClass().getInterfaces()[0].getSimpleName();
    }

    private static String getPrimitive(Class clazz) {
        String name = clazz.getSimpleName();
        if (name.contains("Enum")) return "Enum";
        if (name.contains("Decimal")) return "double";
        if (name.contains("Int")) return "int";
        if (name.contains("Boolean")) return "boolean";
        throw new IllegalArgumentException("Unsupported class " + name);
    }

    /**
     * Create the class source file from the detected variables with proper imports and declarations if EncodedValue
     */
    private static String createClassTemplate(Set<String> priorityVariables, Set<String> speedVariables, EncodedValueLookup lookup) {
        final StringBuilder importSourceCode = new StringBuilder("import com.graphhopper.routing.ev.*;\n");
        importSourceCode.append("import java.util.Map;\n");
        final StringBuilder classSourceCode = new StringBuilder();
        boolean includedAreaImports = false;

        final StringBuilder initSourceCode = new StringBuilder("this.avg_speed_enc = avgSpeedEnc;\n");
        Set<String> set = new HashSet<>(priorityVariables);
        set.addAll(speedVariables);
        Set<String> alreadyDone = new HashSet<>();
        String packageName = "com.graphhopper.routing.ev";
        for (String arg : set) {
            if (lookup.hasEncodedValue(arg)) {
                EncodedValue enc = lookup.getEncodedValue(arg, EncodedValue.class);
                if (!EncodingManager.isSharedEV(enc))
                    continue;
                String className = toCamelCase(arg);
                if (arg.endsWith(RouteNetwork.key("")))
                    className = RouteNetwork.class.getSimpleName();

                if (!alreadyDone.contains(className)) {
                    importSourceCode.append("import static " + packageName + "." + className + ".*;\n");
                    alreadyDone.add(className);
                }
                classSourceCode.append("protected " + enc.getClass().getSimpleName() + " " + arg + "_enc;\n");
                initSourceCode.append("if (lookup.hasEncodedValue(\"" + arg + "\")) ");
                initSourceCode.append(arg + "_enc = (" + enc.getClass().getSimpleName() + ") lookup.getEncodedValue(\"" + arg + "\", "
                        + className + ".class);\n");
            } else if (arg.startsWith(AREA_PREFIX)) {
                if (!includedAreaImports) {
                    importSourceCode.append("import " + BBox.class.getName() + ";\n");
                    importSourceCode.append("import " + GHUtility.class.getName() + ";\n");
                    importSourceCode.append("import " + PreparedGeometryFactory.class.getName() + ";\n");
                    importSourceCode.append("import " + JsonFeature.class.getName() + ";\n");
                    importSourceCode.append("import " + Polygon.class.getName() + ";\n");
                    includedAreaImports = true;
                }

                String id = arg.substring(AREA_PREFIX.length());
                String varName = "area_" + id;
                classSourceCode.append("protected " + Polygon.class.getSimpleName() + " " + varName + ";\n");
                initSourceCode.append("JsonFeature feature = (JsonFeature) areas.get(\"" + id + "\");\n");
                initSourceCode.append("if(feature == null) throw new IllegalArgumentException(\"Area '" + id + "' wasn't found\");\n");
                initSourceCode.append(varName + " = new Polygon(new PreparedGeometryFactory().create(feature.getGeometry()));\n");
            } else {
                if (!isValidVariableName(arg))
                    throw new IllegalArgumentException("Variable not supported: " + arg);
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
                + "   public void init(EncodedValueLookup lookup, "
                + DecimalEncodedValue.class.getName() + " avgSpeedEnc, Map<String, JsonFeature> areas) {\n"
                + initSourceCode
                + "   }\n\n"
                // we need these placeholder methods so that the hooks in DeepCopier are invoked
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
     * 1. check user expressions via Parser.parseConditionalExpression and only allow whitelisted variables and methods.
     * 2. while this check it also guesses the variable names and stores it in createObjects
     * 3. creates if-then-elseif expressions from the checks and returns them as BlockStatements
     *
     * @return the created if-then (and elseif) expressions
     */
    private static List<Java.BlockStatement> verifyExpressions(StringBuilder expressions, String info, Set<String> createObjects,
                                                               Map<String, Object> map, EncodedValueLookup lookup,
                                                               CodeBuilder codeBuilder, String lastStmt) throws Exception {
        // TODO can or should we reuse Java.Atom created in parseAndGuessParametersFromCondition?
        internalVerifyExpressions(expressions, info, createObjects, map, lookup, codeBuilder, lastStmt, false);
        return new Parser(new Scanner(info, new StringReader(expressions.toString()))).
                parseBlockStatements();
    }

    private static void internalVerifyExpressions(StringBuilder expressions, String info, Set<String> createObjects,
                                                  Map<String, Object> map, EncodedValueLookup lookup,
                                                  CodeBuilder codeBuilder, String lastStmt, boolean firstMatch) {
        if (!(map instanceof LinkedHashMap))
            throw new IllegalArgumentException("map needs to be ordered for " + info + " but was " + map.getClass().getSimpleName());
        // allow variables, all encoded values, constants
        NameValidator nameInConditionValidator = name -> lookup.hasEncodedValue(name)
                || name.toUpperCase(Locale.ROOT).equals(name) || isValidVariableName(name);

        int count = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String expression = entry.getKey();
            if (expression.equals(CustomWeightingOld.CATCH_ALL))
                throw new IllegalArgumentException("replace all '*' expressions with 'true'");
            if (firstMatch) {
                if ("true".equals(expression) && count + 1 != map.size())
                    throw new IllegalArgumentException("'true' in " + FIRST_MATCH + " must come as last expression but was " + count);
            } else if (expression.equals(FIRST_MATCH)) { // do not allow further nested blocks
                if (!(entry.getValue() instanceof LinkedHashMap))
                    throw new IllegalArgumentException("entries for " + expression + " in " + info + " are invalid");
                internalVerifyExpressions(expressions, info + " " + expression, createObjects,
                        (Map<String, Object>) entry.getValue(), lookup, codeBuilder, "", true);
                continue;
            }

            if (!parseAndGuessParametersFromCondition(createObjects, expression, nameInConditionValidator))
                throw new IllegalArgumentException("key is an invalid simple condition: " + expression);
            Object numberObj = entry.getValue();
            if (!(numberObj instanceof Number))
                throw new IllegalArgumentException("value is not a Number " + numberObj);
            Number number = (Number) numberObj;
            if (firstMatch && count > 0)
                expressions.append("else ");
            expressions.append("if (" + expression + ") {" + codeBuilder.create(number) + "}\n");
            count++;
        }
        expressions.append(lastStmt + "\n");
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

    static String toCamelCase(String arg) {
        if (arg.isEmpty()) throw new IllegalArgumentException("Cannot be empty");
        String clazz = Helper.underScoreToCamelCase(arg);
        return Character.toUpperCase(clazz.charAt(0)) + clazz.substring(1);
    }

    /**
     * Enforce simple expressions of user input to increase security.
     *
     * @param returnSet collects guess parameters
     * @return true if valid and "simple" expression
     */
    static boolean parseAndGuessParametersFromCondition(Set<String> returnSet, String expression, NameValidator validator) {
        if (expression.length() > 100)
            return false;
        try {
            Parser parser = new Parser(new Scanner("ignore", new StringReader(expression)));
            Java.Atom atom = parser.parseConditionalExpression();
            // after parsing the expression the input should end (otherwise it is not "simple")
            if (parser.peek().type == TokenType.END_OF_INPUT)
                return atom.accept(new MyConditionVisitor(returnSet, validator));
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    private static class MyConditionVisitor implements Visitor.AtomVisitor<Boolean, Exception> {
        private final Set<String> parameters;
        private final NameValidator nameValidator;
        private final Set<String> allowedMethods = new HashSet<>(Arrays.asList("ordinal", "getDistance", "getName",
                "contains", "in", "sqrt", "abs"));

        public MyConditionVisitor(Set<String> parameters, NameValidator nameValidator) {
            this.parameters = parameters;
            this.nameValidator = nameValidator;
        }

        @Override
        public Boolean visitRvalue(Java.Rvalue rv) throws Exception {
            if (rv instanceof Java.AmbiguousName) {
                Java.AmbiguousName n = (Java.AmbiguousName) rv;
                for (String identifier : n.identifiers) {
                    // allow only certain methods and other identifiers (constants and like encoded values)
                    if (nameValidator.isValid(identifier)) {
                        if (!Character.isUpperCase(identifier.charAt(0)))
                            parameters.add(n.identifiers[0]);
                        return true;
                    }
                }
                return false;
            }
            if (rv instanceof Java.Literal)
                return true;
            if (rv instanceof Java.MethodInvocation) {
                Java.MethodInvocation mi = (Java.MethodInvocation) rv;
                if (allowedMethods.contains(mi.methodName)) {
                    // class methods like in() only have an implicit "this"
                    if (mi.target == null) {
                        for (Java.Rvalue methodArg : mi.arguments) {
                            // allow only simple method arguments i.e. no methods etc
                            if (!(methodArg instanceof Java.AmbiguousName)) return false;
                            if (!visitRvalue(methodArg)) return false;
                        }
                        return true;
                    }
                    return mi.target.accept(this); // Math.sqrt
                }
                return false;
            }
            if (rv instanceof Java.BinaryOperation)
                if (((Java.BinaryOperation) rv).lhs.accept(this))
                    return ((Java.BinaryOperation) rv).rhs.accept(this);
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
    }

    interface NameValidator {
        boolean isValid(String name);
    }

    interface CodeBuilder {
        String create(Number n);
    }
}
