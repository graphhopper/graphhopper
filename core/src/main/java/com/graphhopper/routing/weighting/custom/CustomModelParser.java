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

import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.JsonFeature;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.Polygon;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.Location;
import org.codehaus.commons.compiler.io.Readers;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.*;
import org.codehaus.janino.util.DeepCopier;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class CustomModelParser {
    private static final AtomicLong longVal = new AtomicLong(1);
    static final String IN_AREA_PREFIX = "in_area_";
    private static final Set<String> allowedNames = new HashSet<>(Arrays.asList("edge", "Math"));
    private static final boolean JANINO_DEBUG = Boolean.getBoolean(Scanner.SYSTEM_PROPERTY_SOURCE_DEBUGGING_ENABLE);

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

    public static CustomWeighting createWeighting(FlagEncoder baseFlagEncoder, EncodedValueLookup lookup, TurnCostProvider turnCostProvider,
                                                  CustomModel customModel) {
        if (customModel == null)
            throw new IllegalStateException("CustomModel cannot be null");
        CustomWeighting.Parameters parameters = createWeightingParameters(customModel, lookup, baseFlagEncoder.getMaxSpeed(), baseFlagEncoder.getAverageSpeedEnc());
        return new CustomWeighting(baseFlagEncoder, turnCostProvider, parameters);
    }

    /**
     * This method compiles a new subclass of CustomWeightingHelper composed from the provided CustomModel caches this
     * and returns an instance.
     */
    static CustomWeighting.Parameters createWeightingParameters(CustomModel customModel, EncodedValueLookup lookup, double globalMaxSpeed,
                                                                DecimalEncodedValue avgSpeedEnc) {
        String key = customModel.toString() + ",global:" + globalMaxSpeed;
        if (key.length() > 100_000) throw new IllegalArgumentException("Custom Model too big: " + key.length());

        Class<?> clazz = customModel.isInternal() ? INTERNAL_CACHE.get(key) : null;
        if (CACHE_SIZE > 0 && clazz == null)
            clazz = CACHE.get(key);
        if (clazz == null) {
            clazz = createClazz(customModel, lookup, globalMaxSpeed);
            if (customModel.isInternal()) {
                INTERNAL_CACHE.put(key, clazz);
                if (INTERNAL_CACHE.size() > 100) {
                    CACHE.putAll(INTERNAL_CACHE);
                    INTERNAL_CACHE.clear();
                    LoggerFactory.getLogger(CustomModelParser.class).warn("Internal cache must stay small but was "
                            + INTERNAL_CACHE.size() + ". Cleared it. Misuse of CustomModel::__internal_cache?");
                }
            } else if (CACHE_SIZE > 0) {
                CACHE.put(key, clazz);
            }
        }

        try {
            // The class does not need to be thread-safe as we create an instance per request
            CustomWeightingHelper prio = (CustomWeightingHelper) clazz.getDeclaredConstructor().newInstance();
            prio.init(lookup, avgSpeedEnc, customModel.getAreas());
            return new CustomWeighting.Parameters(prio::getSpeed, prio::getPriority, findMaxSpeed(customModel, globalMaxSpeed),
                    customModel.getDistanceInfluence(), customModel.getHeadingPenalty());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("Cannot compile expression " + ex.getMessage(), ex);
        }
    }

    private static Class<?> createClazz(CustomModel customModel, EncodedValueLookup lookup, double globalMaxSpeed) {
        try {
            HashSet<String> priorityVariables = new LinkedHashSet<>();
            List<Java.BlockStatement> priorityStatements = createGetPriorityStatements(priorityVariables, customModel, lookup);
            HashSet<String> speedVariables = new LinkedHashSet<>();
            List<Java.BlockStatement> speedStatements = createGetSpeedStatements(speedVariables, customModel, lookup, globalMaxSpeed);
            // Create different class name, which is required only for debugging.
            // TODO does it improve performance too? I.e. it could be that the JIT is confused if different classes
            //  have the same name and it mixes performance stats. See https://github.com/janino-compiler/janino/issues/137
            long counter = longVal.incrementAndGet();
            String classTemplate = createClassTemplate(counter, priorityVariables, speedVariables, lookup, customModel);
            Java.CompilationUnit cu = (Java.CompilationUnit) new Parser(new Scanner("source", new StringReader(classTemplate))).
                    parseAbstractCompilationUnit();
            cu = injectStatements(priorityStatements, speedStatements, cu);
            SimpleCompiler sc = createCompiler(counter, cu);
            return sc.getClassLoader().loadClass("com.graphhopper.routing.weighting.custom.JaninoCustomWeightingHelperSubclass" + counter);
        } catch (Exception ex) {
            String errString = "Cannot compile expression";
            if (ex instanceof CompileException)
                errString += ", in " + ((CompileException) ex).getLocation().getFileName();
            throw new IllegalArgumentException(errString + " " + ex.getMessage(), ex);
        }
    }

    /**
     * Parse the expressions from CustomModel relevant for the method getSpeed - see createClassTemplate.
     *
     * @return the created statements (parsed expressions)
     */
    private static List<Java.BlockStatement> createGetSpeedStatements(Set<String> speedVariables,
                                                                      CustomModel customModel, EncodedValueLookup lookup,
                                                                      double globalMaxSpeed) throws Exception {
        List<Java.BlockStatement> speedStatements = new ArrayList<>();
        speedStatements.addAll(verifyExpressions(new StringBuilder(), "in 'speed' entry, ", speedVariables,
                customModel.getSpeed(), lookup, "return Math.min(value, " + globalMaxSpeed + ");\n"));
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
        List<Java.BlockStatement> priorityStatements = new ArrayList<>();
        priorityStatements.addAll(verifyExpressions(new StringBuilder("double value = 1;\n"), "in 'priority' entry, ",
                priorityVariables, customModel.getPriority(), lookup, "return value;"));
        String priorityMethodStartBlock = "";
        for (String arg : priorityVariables) {
            priorityMethodStartBlock += getVariableDeclaration(lookup, arg);
        }
        priorityStatements.addAll(0, new Parser(new org.codehaus.janino.Scanner("getPriority", new StringReader(priorityMethodStartBlock))).
                parseBlockStatements());
        return priorityStatements;
    }

    static boolean isValidVariableName(String name) {
        return name.startsWith(IN_AREA_PREFIX) || allowedNames.contains(name);
    }

    /**
     * For the methods getSpeed and getPriority we declare variables that contain the encoded value of the current edge
     * or if an area contains the current edge.
     */
    private static String getVariableDeclaration(EncodedValueLookup lookup, String arg) {
        if (lookup.hasEncodedValue(arg)) {
            EncodedValue enc = lookup.getEncodedValue(arg, EncodedValue.class);
            return getReturnType(enc) + " " + arg + " = reverse ? " +
                    "edge.getReverse((" + getInterface(enc) + ") this." + arg + "_enc) : " +
                    "edge.get((" + getInterface(enc) + ") this." + arg + "_enc);\n";
        } else if (isValidVariableName(arg)) {
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
        throw new IllegalArgumentException("Unsupported EncodedValue " + name);
    }

    /**
     * Create the class source file from the detected variables (priorityVariables and speedVariables). We assume that
     * these variables are safe although they are user input because we collected them from parsing via Janino. This
     * means that the source file is free from user input and could be directly compiled. Before we do this we still
     * have to inject that parsed and safe user expressions in a later step.
     */
    private static String createClassTemplate(long counter, Set<String> priorityVariables, Set<String> speedVariables,
                                              EncodedValueLookup lookup, CustomModel customModel) {
        final StringBuilder importSourceCode = new StringBuilder("import com.graphhopper.routing.ev.*;\n");
        importSourceCode.append("import java.util.Map;\n");
        final StringBuilder classSourceCode = new StringBuilder(100);
        boolean includedAreaImports = false;

        final StringBuilder initSourceCode = new StringBuilder("this.avg_speed_enc = avgSpeedEnc;\n");
        Set<String> set = new HashSet<>(priorityVariables);
        set.addAll(speedVariables);
        for (String arg : set) {
            if (lookup.hasEncodedValue(arg)) {
                EncodedValue enc = lookup.getEncodedValue(arg, EncodedValue.class);
                classSourceCode.append("protected " + enc.getClass().getSimpleName() + " " + arg + "_enc;\n");
                initSourceCode.append("if (lookup.hasEncodedValue(\"" + arg + "\")) ");
                initSourceCode.append("this." + arg + "_enc = (" + enc.getClass().getSimpleName()
                        + ") lookup.getEncodedValue(\"" + arg + "\", EncodedValue.class);\n");
            } else if (arg.startsWith(IN_AREA_PREFIX)) {
                if (!includedAreaImports) {
                    importSourceCode.append("import " + BBox.class.getName() + ";\n");
                    importSourceCode.append("import " + GHUtility.class.getName() + ";\n");
                    importSourceCode.append("import " + PreparedGeometryFactory.class.getName() + ";\n");
                    importSourceCode.append("import " + JsonFeature.class.getName() + ";\n");
                    importSourceCode.append("import " + Polygon.class.getName() + ";\n");
                    includedAreaImports = true;
                }

                String id = arg.substring(IN_AREA_PREFIX.length());
                if (!EncodingManager.isValidEncodedValue(id))
                    throw new IllegalArgumentException("Area has invalid name: " + arg);
                if (!customModel.getAreas().containsKey(id))
                    throw new IllegalArgumentException("Area '" + id + "' wasn't found");
                classSourceCode.append("protected " + Polygon.class.getSimpleName() + " " + arg + ";\n");
                initSourceCode.append("JsonFeature feature = (JsonFeature) areas.get(\"" + id + "\");\n");
                initSourceCode.append("this." + arg + " = new Polygon(new PreparedGeometryFactory().create(feature.getGeometry()));\n");
            } else {
                if (!isValidVariableName(arg))
                    throw new IllegalArgumentException("Variable not supported: " + arg);
            }
        }

        return ""
                + "package com.graphhopper.routing.weighting.custom;"
                + "import " + CustomWeightingHelper.class.getName() + ";\n"
                + "import " + EncodedValueLookup.class.getName() + ";\n"
                + "import " + EdgeIteratorState.class.getName() + ";\n"
                + importSourceCode
                + "\npublic class JaninoCustomWeightingHelperSubclass" + counter + " extends " + CustomWeightingHelper.class.getSimpleName() + " {\n"
                + classSourceCode
                + "   @Override\n"
                + "   public void init(EncodedValueLookup lookup, "
                + DecimalEncodedValue.class.getName() + " avgSpeedEnc, Map<String, " + JsonFeature.class.getName() + "> areas) {\n"
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
                                                               List<Statement> list, EncodedValueLookup lookup,
                                                               String lastStmt) throws Exception {
        // allow variables, all encoded values, constants
        ExpressionVisitor.NameValidator nameInConditionValidator = name -> lookup.hasEncodedValue(name)
                || name.toUpperCase(Locale.ROOT).equals(name) || isValidVariableName(name);
        ExpressionVisitor.parseExpressions(expressions, nameInConditionValidator, info, createObjects, list, lookup, lastStmt);
        return new Parser(new org.codehaus.janino.Scanner(info, new StringReader(expressions.toString()))).
                parseBlockStatements();
    }

    /**
     * Injects the already parsed expressions (converted to BlockStatement) via janinos DeepCopier to the provided
     * CompilationUnit cu (a class file).
     */
    private static Java.CompilationUnit injectStatements(List<Java.BlockStatement> priorityStatements,
                                                         List<Java.BlockStatement> speedStatements,
                                                         Java.CompilationUnit cu) throws CompileException {
        cu = new DeepCopier() {
            boolean speedInjected = false;
            boolean priorityInjected = false;

            @Override
            public Java.FieldDeclaration copyFieldDeclaration(Java.FieldDeclaration subject) throws CompileException {
                // for https://github.com/janino-compiler/janino/issues/135
                Java.FieldDeclaration fd = super.copyFieldDeclaration(subject);
                fd.setEnclosingScope(subject.getEnclosingScope());
                return fd;
            }

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
                File dir = new File("./src/main/java/com/graphhopper/routing/weighting/custom");
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

    static double findMaxSpeed(CustomModel customModel, final double maxSpeed) {
        double globalMin_maxSpeed = maxSpeed;
        double blockMax_maxSpeed = 0;
        for (Statement statement : customModel.getSpeed()) {
            // Lowering the max_speed estimate for 'limit to' only (TODO later also for MULTIPLY)
            if (statement.getOperation() == Statement.Op.LIMIT) {
                if (statement.getValue() > maxSpeed)
                    throw new IllegalArgumentException("Can never apply 'limit to': " + statement.getValue()
                            + " because maximum vehicle speed is " + maxSpeed);

                switch (statement.getKeyword()) {
                    case IF:
                        if ("true".equals(statement.getExpression())) {
                            blockMax_maxSpeed = globalMin_maxSpeed = Math.min(statement.getValue(), maxSpeed);
                        } else {
                            blockMax_maxSpeed = statement.getValue();
                        }
                        break;
                    case ELSEIF:
                        blockMax_maxSpeed = Math.max(blockMax_maxSpeed, statement.getValue());
                        break;
                    case ELSE:
                        blockMax_maxSpeed = Math.max(blockMax_maxSpeed, statement.getValue());
                        globalMin_maxSpeed = Math.min(globalMin_maxSpeed, blockMax_maxSpeed);
                        break;
                    default:
                        throw new IllegalArgumentException("unknown keyword " + statement.getKeyword());
                }

                if (globalMin_maxSpeed <= 0)
                    throw new IllegalArgumentException("speed is always limited to 0. This must not be but results from " + statement);
            }
        }

        return globalMin_maxSpeed;
    }
}
