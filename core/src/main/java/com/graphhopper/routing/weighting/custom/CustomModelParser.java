/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.MinMax;
import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.Polygon;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.Location;
import org.codehaus.commons.compiler.io.Readers;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.*;
import org.codehaus.janino.util.DeepCopier;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.prep.PreparedPolygon;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class CustomModelParser {
    private static final AtomicLong longVal = new AtomicLong(1);
    static final String IN_AREA_PREFIX = "in_";
    static final String BACKWARD_PREFIX = "backward_";
    private static final boolean JANINO_DEBUG = Boolean.getBoolean(Scanner.SYSTEM_PROPERTY_SOURCE_DEBUGGING_ENABLE);
    private static final String SCRIPT_FILE_DIR = System.getProperty(Scanner.SYSTEM_PROPERTY_SOURCE_DEBUGGING_DIR, "./src/main/java/com/graphhopper/routing/weighting/custom");

    // Without a cache the class creation takes 10-40ms which makes routingLM8 requests 20% slower on average.
    // CH requests and preparation is unaffected as cached weighting from preparation is used.
    // Use accessOrder==true to remove oldest accessed entry, not oldest inserted.
    private static final int CACHE_SIZE = Integer.getInteger("graphhopper.custom_weighting.cache_size", 1000);
    private static final Map<String, Class<?>> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, Class<?>>(CACHE_SIZE, 0.75f, true) {
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    // This internal cache ensures that the "internal" Weighting classes specified in the profiles, are never removed regardless
    // of how frequent other Weightings are created and accessed. We only need to synchronize the get and put methods alone.
    // E.g. we do not care for the race condition where two identical classes are requested and one of them is overwritten.
    // TODO perf compare with ConcurrentHashMap, but I guess, if there is a difference at all, it is not big for small maps
    private static final Map<String, Class<?>> INTERNAL_CACHE = Collections.synchronizedMap(new HashMap<>());

    private CustomModelParser() {
        // utility class
    }

    public static CustomWeighting createWeighting(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, DecimalEncodedValue priorityEnc,
                                                  EncodedValueLookup lookup, TurnCostProvider turnCostProvider, CustomModel customModel) {
        if (customModel == null)
            throw new IllegalStateException("CustomModel cannot be null");
        double maxSpeed = speedEnc.getMaxOrMaxStorableDecimal();
        CustomWeighting.Parameters parameters = createWeightingParameters(customModel, lookup, speedEnc, maxSpeed, priorityEnc);
        return new CustomWeighting(accessEnc, speedEnc, turnCostProvider, parameters);
    }

    /**
     * This method compiles a new subclass of CustomWeightingHelper composed from the provided CustomModel caches this
     * and returns an instance.
     *
     * @param priorityEnc can be null
     */
    static CustomWeighting.Parameters createWeightingParameters(CustomModel customModel, EncodedValueLookup lookup,
                                                                DecimalEncodedValue avgSpeedEnc, double globalMaxSpeed,
                                                                DecimalEncodedValue priorityEnc) {

        double globalMaxPriority = priorityEnc == null ? 1 : priorityEnc.getMaxStorableDecimal();
        // if the same custom model is used with a different base profile we cannot use the cached version
        String key = customModel + ",speed:" + avgSpeedEnc.getName() + ",global_max_speed:" + globalMaxSpeed
                + (priorityEnc == null ? "" : "prio:" + priorityEnc.getName() + ",global_max_priority:" + globalMaxPriority);
        if (key.length() > 100_000) throw new IllegalArgumentException("Custom Model too big: " + key.length());

        Class<?> clazz = customModel.isInternal() ? INTERNAL_CACHE.get(key) : null;
        if (CACHE_SIZE > 0 && clazz == null)
            clazz = CACHE.get(key);
        if (clazz == null) {
            clazz = createClazz(customModel, lookup, globalMaxSpeed, globalMaxPriority);
            if (customModel.isInternal()) {
                INTERNAL_CACHE.put(key, clazz);
                if (INTERNAL_CACHE.size() > 100) {
                    CACHE.putAll(INTERNAL_CACHE);
                    INTERNAL_CACHE.clear();
                    LoggerFactory.getLogger(CustomModelParser.class).warn("Internal cache must stay small but was "
                            + INTERNAL_CACHE.size() + ". Cleared it. Misuse of CustomModel::internal?");
                }
            } else if (CACHE_SIZE > 0) {
                CACHE.put(key, clazz);
            }
        }

        try {
            // The class does not need to be thread-safe as we create an instance per request
            CustomWeightingHelper prio = (CustomWeightingHelper) clazz.getDeclaredConstructor().newInstance();
            prio.init(lookup, avgSpeedEnc, priorityEnc, CustomModel.getAreasAsMap(customModel.getAreas()));
            return new CustomWeighting.Parameters(prio::getSpeed, prio::getPriority, prio.getMaxSpeed(), prio.getMaxPriority(),
                    customModel.getDistanceInfluence() == null ? 0 : customModel.getDistanceInfluence(), customModel.getHeadingPenalty());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("Cannot compile expression " + ex.getMessage(), ex);
        }
    }

    /**
     * This method does the following:
     * <ul>
     * <li>0. optionally we already checked the right-hand side expressions before this method call in FindMinMax.checkLMConstraints
     *     (only the client-side custom model statements)
     * </li>
     * <li>1. determine minimum and maximum values via parsing the right-hand side expression -> done in ValueExpressionVisitor.
     *     We need the maximum values for a simple negative check AND for the CustomWeighting.Parameters which is for
     *     Weighting.getMinWeight which is for A*. Note: we could make this step optional somehow for other algorithms,
     *     but parsing would be still required in the next step for security reasons.
     * </li>
     * <li>2. parse condition value of priority and speed statements -> done in ConditionalExpressionVisitor (don't parse RHS expressions again)
     * </li>
     * <li>3. create class template as String, inject the created statements and create the Class
     * </li>
     * </ul>
     */
    private static Class<?> createClazz(CustomModel customModel, EncodedValueLookup lookup,
                                        double globalMaxSpeed, double globalMaxPriority) {
        try {
            HashSet<String> priorityVariables = new LinkedHashSet<>();
            // initial value of minimum has to be >0 so that multiple_by with a negative value leads to a negative value and not 0
            MinMax minMaxPriority = new MinMax(1, globalMaxPriority);
            FindMinMax.findMinMax(priorityVariables, minMaxPriority, customModel.getPriority(), lookup);
            if (minMaxPriority.min < 0)
                throw new IllegalArgumentException("priority has to be >=0 but can be negative (" + minMaxPriority.min + ")");
            if (minMaxPriority.max < 0)
                throw new IllegalArgumentException("maximum priority has to be >=0 but was " + minMaxPriority.max);
            List<Java.BlockStatement> priorityStatements = createGetPriorityStatements(priorityVariables, customModel, lookup);

            HashSet<String> speedVariables = new LinkedHashSet<>();
            MinMax minMaxSpeed = new MinMax(1, globalMaxSpeed);
            FindMinMax.findMinMax(speedVariables, minMaxSpeed, customModel.getSpeed(), lookup);
            if (minMaxSpeed.min < 0)
                throw new IllegalArgumentException("speed has to be >=0 but can be negative (" + minMaxSpeed.min + ")");
            if (minMaxSpeed.max <= 0)
                throw new IllegalArgumentException("maximum speed has to be >0 but was " + minMaxSpeed.max);
            List<Java.BlockStatement> speedStatements = createGetSpeedStatements(speedVariables, customModel, lookup);
            // Create different class name, which is required only for debugging.
            // TODO does it improve performance too? I.e. it could be that the JIT is confused if different classes
            //  have the same name and it mixes performance stats. See https://github.com/janino-compiler/janino/issues/137
            long counter = longVal.incrementAndGet();
            String classTemplate = createClassTemplate(counter, priorityVariables, minMaxPriority.max, speedVariables, minMaxSpeed.max,
                    lookup, CustomModel.getAreasAsMap(customModel.getAreas()));
            Java.CompilationUnit cu = (Java.CompilationUnit) new Parser(new Scanner("source", new StringReader(classTemplate))).
                    parseAbstractCompilationUnit();
            cu = injectStatements(priorityStatements, speedStatements, cu);
            SimpleCompiler sc = createCompiler(counter, cu);
            return sc.getClassLoader().loadClass("com.graphhopper.routing.weighting.custom.JaninoCustomWeightingHelperSubclass" + counter);
        } catch (Exception ex) {
            String errString = "Cannot compile expression";
            throw new IllegalArgumentException(errString + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Parse the expressions from CustomModel relevant for the method getSpeed - see createClassTemplate.
     *
     * @return the created statements (parsed expressions)
     */
    private static List<Java.BlockStatement> createGetSpeedStatements(Set<String> speedVariables,
                                                                      CustomModel customModel, EncodedValueLookup lookup) throws Exception {
        List<Java.BlockStatement> speedStatements = new ArrayList<>(verifyExpressions(new StringBuilder(),
                "speed entry", speedVariables, customModel.getSpeed(), lookup));
        String speedMethodStartBlock = "double value = super.getRawSpeed(edge, reverse);\n";
        // a bit inefficient to possibly define variables twice, but for now we have two separate methods
        for (String arg : speedVariables) {
            speedMethodStartBlock += getVariableDeclaration(lookup, arg);
        }
        speedStatements.addAll(0, new Parser(new org.codehaus.janino.Scanner("getSpeed", new StringReader(speedMethodStartBlock))).
                parseBlockStatements());
        return speedStatements;
    }

    /**
     * Parse the expressions from CustomModel relevant for the method getPriority - see createClassTemplate.
     *
     * @return the created statements (parsed expressions)
     */
    private static List<Java.BlockStatement> createGetPriorityStatements(Set<String> priorityVariables,
                                                                         CustomModel customModel, EncodedValueLookup lookup) throws Exception {
        List<Java.BlockStatement> priorityStatements = new ArrayList<>(verifyExpressions(new StringBuilder(),
                "priority entry", priorityVariables, customModel.getPriority(), lookup));
        String priorityMethodStartBlock = "double value = super.getRawPriority(edge, reverse);\n";
        for (String arg : priorityVariables) {
            priorityMethodStartBlock += getVariableDeclaration(lookup, arg);
        }
        priorityStatements.addAll(0, new Parser(new org.codehaus.janino.Scanner("getPriority", new StringReader(priorityMethodStartBlock))).
                parseBlockStatements());
        return priorityStatements;
    }

    /**
     * For the methods getSpeed and getPriority we declare variables that contain the encoded value of the current edge
     * or if an area contains the current edge.
     */
    private static String getVariableDeclaration(EncodedValueLookup lookup, final String arg) {
        if (lookup.hasEncodedValue(arg)) {
            EncodedValue enc = lookup.getEncodedValue(arg, EncodedValue.class);
            return getReturnType(enc) + " " + arg + " = reverse ? " +
                    "edge.getReverse((" + getInterface(enc) + ") this." + arg + "_enc) : " +
                    "edge.get((" + getInterface(enc) + ") this." + arg + "_enc);\n";
        } else if (arg.startsWith(BACKWARD_PREFIX)) {
            final String argSubstr = arg.substring(BACKWARD_PREFIX.length());
            if (lookup.hasEncodedValue(argSubstr)) {
                EncodedValue enc = lookup.getEncodedValue(argSubstr, EncodedValue.class);
                return getReturnType(enc) + " " + arg + " = reverse ? " +
                        "edge.get((" + getInterface(enc) + ") this." + argSubstr + "_enc) : " +
                        "edge.getReverse((" + getInterface(enc) + ") this." + argSubstr + "_enc);\n";
            } else {
                throw new IllegalArgumentException("Not supported for backward: " + argSubstr);
            }
        } else if (arg.startsWith(IN_AREA_PREFIX)) {
            return "";
        } else {
            throw new IllegalArgumentException("Not supported " + arg);
        }
    }

    /**
     * @return the interface as string of the provided EncodedValue, e.g. IntEncodedValue (only interface) or
     * BooleanEncodedValue (first interface). For StringEncodedValue we return IntEncodedValue to return the index
     * instead of the String for faster comparison.
     */
    private static String getInterface(EncodedValue enc) {
        if (enc instanceof StringEncodedValue) return IntEncodedValue.class.getSimpleName();
        if (enc.getClass().getInterfaces().length == 0) return enc.getClass().getSimpleName();
        return enc.getClass().getInterfaces()[0].getSimpleName();
    }

    private static String getReturnType(EncodedValue encodedValue) {
        String name = encodedValue.getClass().getSimpleName();
        if (name.contains("Enum")) return "Enum";
        if (name.contains("String")) return "int"; // we use indexOf
        if (name.contains("Decimal")) return "double";
        if (name.contains("Int")) return "int";
        if (name.contains("Boolean")) return "boolean";
        throw new IllegalArgumentException("Unsupported EncodedValue: " + encodedValue.getClass());
    }

    /**
     * Create the class source file from the detected variables (priorityVariables and speedVariables). We assume that
     * these variables are safe although they are user input because we collected them from parsing via Janino. This
     * means that the source file is free from user input and could be directly compiled. Before we do this we still
     * have to inject that parsed and safe user expressions in a later step.
     */
    private static String createClassTemplate(long counter,
                                              Set<String> priorityVariables, double maxPriority,
                                              Set<String> speedVariables, double maxSpeed,
                                              EncodedValueLookup lookup, Map<String, JsonFeature> areas) {
        final StringBuilder importSourceCode = new StringBuilder("import com.graphhopper.routing.ev.*;\n");
        importSourceCode.append("import java.util.Map;\n");
        final StringBuilder classSourceCode = new StringBuilder(100);
        boolean includedAreaImports = false;

        final StringBuilder initSourceCode = new StringBuilder("this.avg_speed_enc = avgSpeedEnc;\n");
        initSourceCode.append("this.priority_enc = priorityEnc;\n");
        Set<String> set = new HashSet<>();
        for (String prioVar : priorityVariables)
            set.add(prioVar.startsWith(BACKWARD_PREFIX) ? prioVar.substring(BACKWARD_PREFIX.length()) : prioVar);
        for (String speedVar : speedVariables)
            set.add(speedVar.startsWith(BACKWARD_PREFIX) ? speedVar.substring(BACKWARD_PREFIX.length()) : speedVar);

        for (String arg : set) {
            if (lookup.hasEncodedValue(arg)) {
                EncodedValue enc = lookup.getEncodedValue(arg, EncodedValue.class);
                classSourceCode.append("protected " + getInterface(enc) + " " + arg + "_enc;\n");
                initSourceCode.append("this." + arg + "_enc = (" + getInterface(enc)
                        + ") lookup.getEncodedValue(\"" + arg + "\", EncodedValue.class);\n");
            } else if (arg.startsWith(IN_AREA_PREFIX)) {
                if (!includedAreaImports) {
                    importSourceCode.append("import " + BBox.class.getName() + ";\n");
                    importSourceCode.append("import " + GHUtility.class.getName() + ";\n");
                    importSourceCode.append("import " + PreparedPolygon.class.getName() + ";\n");
                    importSourceCode.append("import " + Polygonal.class.getName() + ";\n");
                    importSourceCode.append("import " + JsonFeature.class.getName() + ";\n");
                    importSourceCode.append("import " + Polygon.class.getName() + ";\n");
                    includedAreaImports = true;
                }

                if (!JsonFeature.isValidId(arg))
                    throw new IllegalArgumentException("Area has invalid name: " + arg);
                String id = arg.substring(IN_AREA_PREFIX.length());
                JsonFeature feature = areas.get(id);
                if (feature == null)
                    throw new IllegalArgumentException("Area '" + id + "' wasn't found");
                if (feature.getGeometry() == null)
                    throw new IllegalArgumentException("Area '" + id + "' does not contain a geometry");
                if (!(feature.getGeometry() instanceof Polygonal))
                    throw new IllegalArgumentException("Currently only type=Polygon is supported for areas but was " + feature.getGeometry().getGeometryType());
                if (feature.getBBox() != null)
                    throw new IllegalArgumentException("Bounding box of area " + id + " must be empty");
                classSourceCode.append("protected " + Polygon.class.getSimpleName() + " " + arg + ";\n");
                initSourceCode.append("JsonFeature feature_" + id + " = (JsonFeature) areas.get(\"" + id + "\");\n");
                initSourceCode.append("this." + arg + " = new Polygon(new PreparedPolygon((Polygonal) feature_" + id + ".getGeometry()));\n");
            } else {
                if (!arg.startsWith(IN_AREA_PREFIX))
                    throw new IllegalArgumentException("Variable not supported: " + arg);
            }
        }

        return ""
                + "package com.graphhopper.routing.weighting.custom;\n"
                + "import " + CustomWeightingHelper.class.getName() + ";\n"
                + "import " + EncodedValueLookup.class.getName() + ";\n"
                + "import " + EdgeIteratorState.class.getName() + ";\n"
                + importSourceCode
                + "\npublic class JaninoCustomWeightingHelperSubclass" + counter + " extends " + CustomWeightingHelper.class.getSimpleName() + " {\n"
                + classSourceCode
                + "   @Override\n"
                + "   public void init(EncodedValueLookup lookup, " + DecimalEncodedValue.class.getName() + " avgSpeedEnc, "
                + DecimalEncodedValue.class.getName() + " priorityEnc, Map<String, " + JsonFeature.class.getName() + "> areas) {\n"
                + initSourceCode
                + "   }\n\n"
                // we need these placeholder methods so that the hooks in DeepCopier are invoked
                + "   @Override\n"
                + "   public double getPriority(EdgeIteratorState edge, boolean reverse) {\n"
                + "      return 1; //will be overwritten by code injected in DeepCopier\n"
                + "   }\n"
                + "   @Override\n"
                + "   public double getSpeed(EdgeIteratorState edge, boolean reverse) {\n"
                + "      return getRawSpeed(edge, reverse); //will be overwritten by code injected in DeepCopier\n"
                + "   }\n"
                + "   @Override\n"
                + "   protected double getMaxSpeed() {\n"
                + "      return " + maxSpeed + ";"
                + "   }\n"
                + "   @Override\n"
                + "   protected double getMaxPriority() {\n"
                + "      return " + maxPriority + ";"
                + "   }\n"
                + "}";
    }

    /**
     * This method does:
     * 1. check user expressions via Parser.parseConditionalExpression and only allow whitelisted variables and methods.
     * 2. while this check it also guesses the variable names and stores it in createObjects
     * 3. creates if-then-elseif expressions from the checks and returns them as BlockStatements
     *
     * @return the created if-then, else and elseif statements
     */
    private static List<Java.BlockStatement> verifyExpressions(StringBuilder expressions, String info, Set<String> createObjects,
                                                               List<Statement> list, EncodedValueLookup lookup) throws Exception {
        // allow variables, all encoded values, constants and special variables like in_xyarea or backward_car_access
        NameValidator nameInConditionValidator = name -> lookup.hasEncodedValue(name)
                || name.toUpperCase(Locale.ROOT).equals(name) || name.startsWith(IN_AREA_PREFIX)
                || name.startsWith(BACKWARD_PREFIX) && lookup.hasEncodedValue(name.substring(BACKWARD_PREFIX.length()));

        parseExpressions(expressions, nameInConditionValidator, info, createObjects, list);
        return new Parser(new org.codehaus.janino.Scanner(info, new StringReader(expressions.toString()))).
                parseBlockStatements();
    }

    static void parseExpressions(StringBuilder expressions, NameValidator nameInConditionValidator,
                                 String exceptionInfo, Set<String> createObjects, List<Statement> list) {

        for (Statement statement : list) {
            // avoid parsing the RHS value expression again as we just did it to get the maximum values in createClazz
            if (statement.getKeyword() == Statement.Keyword.ELSE) {
                if (!Helper.isEmpty(statement.getCondition()))
                    throw new IllegalArgumentException("condition must be empty but was " + statement.getCondition());

                expressions.append("else {").append(statement.getOperation().build(statement.getValue())).append("; }\n");
            } else if (statement.getKeyword() == Statement.Keyword.ELSEIF || statement.getKeyword() == Statement.Keyword.IF) {
                ParseResult parseResult = ConditionalExpressionVisitor.parse(statement.getCondition(), nameInConditionValidator);
                if (!parseResult.ok)
                    throw new IllegalArgumentException(exceptionInfo + " invalid condition \"" + statement.getCondition() + "\"" +
                            (parseResult.invalidMessage == null ? "" : ": " + parseResult.invalidMessage));
                createObjects.addAll(parseResult.guessedVariables);
                if (statement.getKeyword() == Statement.Keyword.ELSEIF)
                    expressions.append("else ");
                expressions.append("if (").append(parseResult.converted).append(") {").append(statement.getOperation().build(statement.getValue())).append("; }\n");
            } else {
                throw new IllegalArgumentException("The statement must be either 'if', 'else_if' or 'else'");
            }
        }
        expressions.append("return value;\n");
    }

    /**
     * Injects the already parsed expressions (converted to BlockStatement) via Janino's DeepCopier to the provided
     * CompilationUnit cu (a class file).
     */
    private static Java.CompilationUnit injectStatements(List<Java.BlockStatement> priorityStatements,
                                                         List<Java.BlockStatement> speedStatements,
                                                         Java.CompilationUnit cu) throws CompileException {
        cu = new DeepCopier() {
            boolean speedInjected = false;
            boolean priorityInjected = false;

            @Override
            public Java.MethodDeclarator copyMethodDeclarator(Java.MethodDeclarator subject) throws CompileException {
                if (subject.name.equals("getSpeed") && !speedStatements.isEmpty() && !speedInjected) {
                    speedInjected = true;
                    return injectStatements(subject, this, speedStatements);
                } else if (subject.name.equals("getPriority") && !priorityStatements.isEmpty() && !priorityInjected) {
                    priorityInjected = true;
                    return injectStatements(subject, this, priorityStatements);
                } else {
                    return super.copyMethodDeclarator(subject);
                }
            }
        }.copyCompilationUnit(cu);
        return cu;
    }

    private static Java.MethodDeclarator injectStatements(Java.MethodDeclarator subject, DeepCopier deepCopier,
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
            return methodDecl;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static SimpleCompiler createCompiler(long counter, Java.AbstractCompilationUnit cu) throws CompileException {
        if (JANINO_DEBUG) {
            try {
                StringWriter sw = new StringWriter();
                Unparser.unparse(cu, sw);
                // System.out.println(sw.toString());
                File dir = new File(SCRIPT_FILE_DIR);
                File temporaryFile = new File(dir, "JaninoCustomWeightingHelperSubclass" + counter + ".java");
                Reader reader = Readers.teeReader(
                        new StringReader(sw.toString()), // in
                        new FileWriter(temporaryFile),   // out
                        true               // closeWriterOnEoi
                );
                return new SimpleCompiler(temporaryFile.getAbsolutePath(), reader);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        } else {
            SimpleCompiler compiler = new SimpleCompiler();
            // compiler.setWarningHandler((handle, message, location) -> System.out.println(handle + ", " + message + ", " + location));
            compiler.cook(cu);
            return compiler;
        }
    }
}
