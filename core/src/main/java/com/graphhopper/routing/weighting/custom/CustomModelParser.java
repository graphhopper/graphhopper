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
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.Polygon;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.Location;
import org.codehaus.commons.compiler.io.Readers;
import org.codehaus.janino.*;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.util.DeepCopier;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.prep.PreparedPolygon;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static com.graphhopper.json.Statement.Keyword.IF;

public class CustomModelParser {
    private static final AtomicLong longVal = new AtomicLong(1);
    static final String IN_AREA_PREFIX = "in_";
    static final String BACKWARD_PREFIX = "backward_";
    static final String PREV_PREFIX = "prev_";
    static final String CHANGE_ANGLE = "change_angle";
    private static final boolean JANINO_DEBUG = Boolean.getBoolean(Scanner.SYSTEM_PROPERTY_SOURCE_DEBUGGING_ENABLE);
    private static final String SCRIPT_FILE_DIR = System.getProperty(Scanner.SYSTEM_PROPERTY_SOURCE_DEBUGGING_DIR, "./src/main/java/com/graphhopper/routing/weighting/custom");

    // Without a cache the class creation takes 10-40ms which makes routingLM8 requests 20% slower on average.
    // CH requests and preparation is unaffected as cached weighting from preparation is used.
    // Use accessOrder==true to remove oldest accessed entry, not oldest inserted.
    private static final int CACHE_SIZE = Integer.getInteger("graphhopper.custom_weighting.cache_size", 1000);
    private static final Map<String, Class<?>> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
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

    /**
     * This method creates a weighting from a CustomModel that must limit the speed. Either as an
     * unconditional statement <code>{ "if": "true", "limit_to": "car_average_speed" }<code/> or as
     * an if-elseif-else group.
     */
    public static CustomWeighting createWeighting(EncodedValueLookup lookup, TurnCostProvider turnCostProvider, CustomModel customModel) {
        if (customModel == null)
            throw new IllegalStateException("CustomModel cannot be null");

        CustomWeighting.Parameters parameters = createWeightingParameters(customModel, lookup);
        return new CustomWeighting(turnCostProvider, parameters);
    }

    /**
     * This method compiles a new subclass of CustomWeightingHelper composed of the provided CustomModel caches this
     * and returns an instance.
     */
    public static CustomWeighting.Parameters createWeightingParameters(CustomModel customModel, EncodedValueLookup lookup) {
        String key = customModel.toString();
        Class<?> clazz = customModel.isInternal() ? INTERNAL_CACHE.get(key) : null;
        if (CACHE_SIZE > 0 && clazz == null)
            clazz = CACHE.get(key);
        if (clazz == null) {
            clazz = createClazz(customModel, lookup);
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
            prio.init(customModel, lookup, CustomModel.getAreasAsMap(customModel.getAreas()));
            return new CustomWeighting.Parameters(
                    prio::getSpeed, prio::calcMaxSpeed,
                    prio::getPriority, prio::calcMaxPriority,
                    prio::getTurnPenalty,
                    customModel.getDistanceInfluence() == null ? 0 : customModel.getDistanceInfluence(),
                    customModel.getHeadingPenalty() == null ? Parameters.Routing.DEFAULT_HEADING_PENALTY : customModel.getHeadingPenalty());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("Cannot compile expression " + ex.getMessage(), ex);
        }
    }

    /**
     * This method does the following:
     * <ul>
     * <li>
     *     1. parse the value expressions (RHS) to know about additional encoded values ('findVariables')
     *     and check for multiplications with negative values.
     * </li>
     * <li>2. parse conditional expression of priority and speed statements -> done in ConditionalExpressionVisitor (don't parse RHS expressions again)
     * </li>
     * <li>3. create class template as String, inject the created statements and create the Class
     * </li>
     * </ul>
     */
    private static Class<?> createClazz(CustomModel customModel, EncodedValueLookup lookup) {
        try {
            Set<String> priorityVariables = ValueExpressionVisitor.findVariables(customModel.getPriority(), lookup);
            List<Java.BlockStatement> priorityStatements = createGetPriorityStatements(priorityVariables, customModel, lookup);

            if (customModel.getSpeed().isEmpty())
                throw new IllegalArgumentException("At least one initial statement under 'speed' is required.");

            List<Statement> firstGroup = splitIntoGroup(customModel.getSpeed()).get(0);
            if (firstGroup.size() > 1) {
                Statement lastSt = firstGroup.get(firstGroup.size() - 1);
                if (lastSt.operation() != Statement.Op.LIMIT || lastSt.keyword() != Statement.Keyword.ELSE)
                    throw new IllegalArgumentException("The first group needs to end with an 'else' (or contain a single unconditional 'if' statement).");
            } else {
                Statement firstSt = firstGroup.get(0);
                if (!"true".equals(firstSt.condition()) || firstSt.operation() != Statement.Op.LIMIT || firstSt.keyword() != Statement.Keyword.IF)
                    throw new IllegalArgumentException("The first group needs to contain a single unconditional 'if' statement (or end with an 'else').");
            }

            Set<String> speedVariables = ValueExpressionVisitor.findVariables(customModel.getSpeed(), lookup);
            List<Java.BlockStatement> speedStatements = createGetSpeedStatements(speedVariables, customModel, lookup);

            Set<String> turnPenaltyVariables = ValueExpressionVisitor.findVariables(customModel.getTurnPenalty(), lookup);
            List<Java.BlockStatement> turnPenaltyStatements = createGetTurnPenaltyStatements(turnPenaltyVariables, customModel, lookup);

            // Create different class name, which is required only for debugging.
            // TODO does it improve performance too? I.e. it could be that the JIT is confused if different classes
            //  have the same name and it mixes performance stats. See https://github.com/janino-compiler/janino/issues/137
            long counter = longVal.incrementAndGet();
            String classTemplate = createClassTemplate(counter, priorityVariables, speedVariables, turnPenaltyVariables, lookup, CustomModel.getAreasAsMap(customModel.getAreas()));
            Java.CompilationUnit cu = (Java.CompilationUnit) new Parser(new Scanner("source", new StringReader(classTemplate))).
                    parseAbstractCompilationUnit();
            cu = injectStatements(priorityStatements, speedStatements, turnPenaltyStatements, cu);
            SimpleCompiler sc = createCompiler(counter, cu);
            return sc.getClassLoader().loadClass("com.graphhopper.routing.weighting.custom.JaninoCustomWeightingHelperSubclass" + counter);
        } catch (Exception ex) {
            String errString = "Cannot compile expression";
            throw new IllegalArgumentException(errString + ": " + ex.getMessage(), ex);
        }
    }

    public static List<String> findVariablesForEncodedValuesString(CustomModel model, NameValidator nameValidator, ClassHelper classHelper) {
        Set<String> variables = new LinkedHashSet<>();
        // avoid parsing exception for e.g. in_xy
        NameValidator nameValidatorIntern = s -> {
            // some literals are no variables and would throw an exception (encoded value not found)
            if (Character.isUpperCase(s.charAt(0)) || s.startsWith(IN_AREA_PREFIX))
                return true;
            if (nameValidator.isValid(s)) {
                variables.add(s);
                return true;
            }
            return false;
        };
        findVariablesForEncodedValuesString(model.getPriority(), nameValidatorIntern, classHelper);
        findVariablesForEncodedValuesString(model.getSpeed(), nameValidatorIntern, classHelper);
        return new ArrayList<>(variables);
    }

    private static void findVariablesForEncodedValuesString(List<Statement> statements, NameValidator nameValidator, ClassHelper classHelper) {
        List<List<Statement>> groups = CustomModelParser.splitIntoGroup(statements);
        for (List<Statement> group : groups) {
            for (Statement statement : group) {
                if (statement.isBlock()) {
                    findVariablesForEncodedValuesString(statement.doBlock(), nameValidator, classHelper);
                } else {
                    // ignore potential problems; collect only variables in this step
                    ConditionalExpressionVisitor.parse(statement.condition(), nameValidator, classHelper);
                    ValueExpressionVisitor.parse(statement.value(), nameValidator);
                }
            }
        }
    }

    /**
     * Splits the specified list into several lists of statements starting with if.
     * I.e. a group consists of one 'if' and zero or more 'else_if' and 'else' statements.
     */
    static List<List<Statement>> splitIntoGroup(List<Statement> statements) {
        List<List<Statement>> result = new ArrayList<>();
        List<Statement> group = null;
        for (Statement st : statements) {
            if (IF.equals(st.keyword())) result.add(group = new ArrayList<>());
            if (group == null)
                throw new IllegalArgumentException("Every group must start with an if-statement");
            group.add(st);
        }
        return result;
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
        String speedMethodStartBlock = "double value = " + CustomWeightingHelper.GLOBAL_MAX_SPEED + ";\n";
        // potentially we fetch EncodedValues twice (one time here and one time for priority)
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
        for (Statement s : customModel.getPriority()) {
            if (s.operation() == Statement.Op.ADD)
                throw new IllegalArgumentException("'priority' statement must not have the operation 'add'");
        }
        List<Java.BlockStatement> priorityStatements = new ArrayList<>(verifyExpressions(new StringBuilder(),
                "priority entry", priorityVariables, customModel.getPriority(), lookup));
        String priorityMethodStartBlock = "double value = " + CustomWeightingHelper.GLOBAL_PRIORITY + ";\n";
        for (String arg : priorityVariables) {
            priorityMethodStartBlock += getVariableDeclaration(lookup, arg);
        }
        priorityStatements.addAll(0, new Parser(new org.codehaus.janino.Scanner("getPriority", new StringReader(priorityMethodStartBlock))).
                parseBlockStatements());
        return priorityStatements;
    }

    /**
     * Parse the expressions from CustomModel relevant for the method getTurnPenalty - see createClassTemplate.
     *
     * @return the created statements (parsed expressions)
     */
    private static List<Java.BlockStatement> createGetTurnPenaltyStatements(Set<String> turnPenaltyVariables,
                                                                            CustomModel customModel, EncodedValueLookup lookup) throws Exception {
        for (Statement s : customModel.getTurnPenalty()) {
            if (s.operation() == Statement.Op.ADD && s.value().trim().startsWith("-"))
                throw new IllegalArgumentException("The value for the 'add' operation must be positive, but was: " + s.value());
            if (s.isBlock())
                throw new IllegalArgumentException("'turn_penalty' statement cannot be a block (not yet implemented)");
            if (s.operation() != Statement.Op.ADD)
                throw new IllegalArgumentException("'turn_penalty' statement must have the operation 'add' but was: " + s.operation() + " (not yet implemented)");
        }

        List<Java.BlockStatement> turnPenaltyStatements = new ArrayList<>(verifyExpressions(new StringBuilder(),
                "turn_penalty entry", turnPenaltyVariables, customModel.getTurnPenalty(), lookup));
        boolean needTwoDirections = false;
        Function<String, EncodedValue> fct = createSimplifiedLookup(lookup);
        for (String ttv : turnPenaltyVariables) {
            EncodedValue ev = fct.apply(ttv);
            if (ev != null && ev.isStoreTwoDirections() || ttv.equals(CHANGE_ANGLE)) {
                needTwoDirections = true;
                break;
            }
        }

        String turnPenaltyMethodStartBlock = "double value = 0;\n";
        if (needTwoDirections) {
            // Performance optimization: avoid the following two calls if there is no encoded value
            // that stores two directions. The call to isAdjNode is slightly faster than calling
            // getEdgeIteratorState as it avoids creating a new object and accesses only one node
            // but is slightly less safe as it cannot check that at least one node must be
            // identical (the case where getEdgeIteratorState returns null)
            turnPenaltyMethodStartBlock += "boolean inEdgeReverse = !graph.isAdjNode(inEdge, viaNode);\n" +
                    "boolean outEdgeReverse = !graph.isAdjNode(outEdge, viaNode);\n";
        }

        for (String arg : turnPenaltyVariables) {
            turnPenaltyMethodStartBlock += getTurnPenaltyVariableDeclaration(lookup, arg, needTwoDirections);
        }

        // special case for change_angle method call: we need the orientation encoded value
        if (turnPenaltyVariables.contains(CHANGE_ANGLE)) {
            turnPenaltyVariables.remove(CHANGE_ANGLE);
            turnPenaltyVariables.add(Orientation.KEY);
        }

        turnPenaltyStatements.addAll(0, new Parser(new org.codehaus.janino.Scanner("getTurnPenalty", new StringReader(turnPenaltyMethodStartBlock))).
                parseBlockStatements());
        return turnPenaltyStatements;
    }

    /**
     * For the methods getSpeed and getPriority we declare variables that contain the encoded value of the current edge
     * or if an area contains the current edge.
     */
    private static String getVariableDeclaration(EncodedValueLookup lookup, final String arg) {
        if (arg.equals("__kv")) {
            return "Map __kv = edge.getKeyValues();\n";
        } else if (lookup.hasEncodedValue(arg)) {
            // parameters in method getPriority or getSpeed are: EdgeIteratorState edge, boolean reverse
            EncodedValue enc = lookup.getEncodedValue(arg, EncodedValue.class);
            return getReturnType(enc) + " " + arg + " = (" + getReturnType(enc) + ") (reverse ? " +
                    "edge.getReverse((" + getInterface(enc) + ") this." + arg + "_enc) : " +
                    "edge.get((" + getInterface(enc) + ") this." + arg + "_enc));\n";
        } else if (arg.startsWith(BACKWARD_PREFIX)) {
            final String argSubstr = arg.substring(BACKWARD_PREFIX.length());
            if (lookup.hasEncodedValue(argSubstr)) {
                EncodedValue enc = lookup.getEncodedValue(argSubstr, EncodedValue.class);
                return getReturnType(enc) + " " + arg + " = (" + getReturnType(enc) + ") (reverse ? " +
                        "edge.get((" + getInterface(enc) + ") this." + argSubstr + "_enc) : " +
                        "edge.getReverse((" + getInterface(enc) + ") this." + argSubstr + "_enc));\n";
            } else {
                throw new IllegalArgumentException("Not supported for backward: " + argSubstr);
            }
        } else if (arg.startsWith(IN_AREA_PREFIX)) {
            return "";
        } else {
            throw new IllegalArgumentException("Not supported " + arg);
        }
    }

    private static String getTurnPenaltyVariableDeclaration(EncodedValueLookup lookup, final String arg, boolean needTwoDirections) {
        // parameters in method getTurnPenalty are: int inEdge, int viaNode, int outEdge.
        // The variables outEdgeReverse and inEdgeReverse are provided from initial calls if needTwoDirections is true.
        if (arg.equals(CHANGE_ANGLE)) {
            return "double change_angle = CustomWeightingHelper.calcChangeAngle(edgeIntAccess, this.orientation_enc, inEdge, inEdgeReverse, outEdge, outEdgeReverse);\n";
        } else if (lookup.hasEncodedValue(arg)) {
            EncodedValue enc = lookup.getEncodedValue(arg, EncodedValue.class);
            if (!(enc instanceof EnumEncodedValue<?>))
                throw new IllegalArgumentException("Currently only EnumEncodedValues are supported: " + arg);

            return getReturnType(enc) + " " + arg + " = (" + getReturnType(enc) + ") " +
                    "this." + arg + "_enc.getEnum(" + (needTwoDirections ? "outEdgeReverse" : "false") + ", outEdge, edgeIntAccess);\n";
        } else if (arg.startsWith(PREV_PREFIX)) {
            final String argSubstr = arg.substring(PREV_PREFIX.length());
            if (lookup.hasEncodedValue(argSubstr)) {
                EncodedValue enc = lookup.getEncodedValue(argSubstr, EncodedValue.class);
                if (!(enc instanceof EnumEncodedValue<?>))
                    throw new IllegalArgumentException("Currently only EnumEncodedValues are supported: " + arg);

                return getReturnType(enc) + " " + arg + " = (" + getReturnType(enc) + ") " +
                        "this." + argSubstr + "_enc.getEnum(" + (needTwoDirections ? "inEdgeReverse" : "false") + ", inEdge, edgeIntAccess);\n";
            } else {
                throw new IllegalArgumentException("Not supported for prev: " + argSubstr);
            }
        } else {
            throw new IllegalArgumentException("Not supported for turn_penalty: " + arg);
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
        // order is important
        if (encodedValue instanceof EnumEncodedValue) {
            Class cl = ((EnumEncodedValue) encodedValue).getEnumType();
            // use getSimpleName for inbuilt EncodedValues and more readability of generated source
            return cl.getPackage().equals(EnumEncodedValue.class.getPackage()) ? cl.getSimpleName() : cl.getName();
        }
        if (encodedValue instanceof StringEncodedValue) return "int"; // we use indexOf
        if (encodedValue instanceof DecimalEncodedValue) return "double";
        if (encodedValue instanceof BooleanEncodedValue) return "boolean";
        if (encodedValue instanceof IntEncodedValue) return "int";
        throw new IllegalArgumentException("Unsupported EncodedValue: " + encodedValue.getClass());
    }

    /**
     * Create the class source file from the detected variables (priorityVariables and speedVariables). We assume that
     * these variables are safe although they are user input because we collected them from parsing via Janino. This
     * means that the source file is free from user input and could be directly compiled. Before we do this we still
     * have to inject that parsed and safe user expressions in a later step.
     */
    private static String createClassTemplate(long counter,
                                              Set<String> priorityVariables,
                                              Set<String> speedVariables,
                                              Set<String> turnPenaltyVariables,
                                              EncodedValueLookup lookup, Map<String, JsonFeature> areas) {
        final StringBuilder importSourceCode = new StringBuilder("import com.graphhopper.routing.ev.*;\n");
        importSourceCode.append("import java.util.Map;\n");
        importSourceCode.append("import " + CustomModel.class.getName() + ";\n");
        importSourceCode.append("import " + BaseGraph.class.getName() + ";\n");
        importSourceCode.append("import " + EdgeIntAccess.class.getName() + ";\n");
        final StringBuilder classSourceCode = new StringBuilder(100);
        boolean includedAreaImports = false;

        final StringBuilder initSourceCode = new StringBuilder("this.lookup = lookup;\n");
        initSourceCode.append("this.customModel = customModel;\n");
        Set<String> set = new HashSet<>();
        for (String prioVar : priorityVariables)
            set.add(prioVar.startsWith(BACKWARD_PREFIX) ? prioVar.substring(BACKWARD_PREFIX.length()) : prioVar);
        for (String speedVar : speedVariables)
            set.add(speedVar.startsWith(BACKWARD_PREFIX) ? speedVar.substring(BACKWARD_PREFIX.length()) : speedVar);
        for (String speedVar : turnPenaltyVariables)
            set.add(speedVar.startsWith(PREV_PREFIX) ? speedVar.substring(PREV_PREFIX.length()) : speedVar);

        for (String arg : set) {
            if (arg.equals("__kv")) {
                continue; // __kv is a method-local variable, not a class field
            } else if (lookup.hasEncodedValue(arg)) {
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
                + "   public void init(CustomModel customModel, EncodedValueLookup lookup, Map<String, " + JsonFeature.class.getName() + "> areas) {\n"
                + initSourceCode
                + "   }\n\n"
                // we need these placeholder methods so that the hooks in DeepCopier are invoked
                + "   @Override\n"
                + "   public double getPriority(EdgeIteratorState edge, boolean reverse) {\n"
                + "      return 1; //will be overwritten by code injected in DeepCopier\n"
                + "   }\n"
                + "   @Override\n"
                + "   public double getSpeed(EdgeIteratorState edge, boolean reverse) {\n"
                + "      return 1; //will be overwritten by code injected in DeepCopier\n"
                + "   }\n"
                + "   @Override\n"
                + "   public double getTurnPenalty(BaseGraph graph, EdgeIntAccess edgeIntAccess, int inEdge, int viaNode, int outEdge) {\n"
                + "      return 1; //will be overwritten by code injected in DeepCopier\n"
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
                || name.toUpperCase(Locale.ROOT).equals(name) || name.startsWith(IN_AREA_PREFIX) || name.equals(CHANGE_ANGLE)
                || name.equals("__kv")
                || name.startsWith(BACKWARD_PREFIX) && lookup.hasEncodedValue(name.substring(BACKWARD_PREFIX.length()))
                || name.startsWith(PREV_PREFIX) && lookup.hasEncodedValue(name.substring(PREV_PREFIX.length()));
        Function<String, EncodedValue> fct = createSimplifiedLookup(lookup);
        ClassHelper helper = key -> {
            EncodedValue ev = fct.apply(key);
            if (ev == null) throw new IllegalArgumentException("Couldn't find class for " + key);
            return getReturnType(ev);
        };

        parseExpressions(expressions, nameInConditionValidator, info, createObjects, list, helper, "");
        expressions.append("return value;\n");
        return new Parser(new org.codehaus.janino.Scanner(info, new StringReader(expressions.toString()))).
                parseBlockStatements();
    }

    private static Function<String, EncodedValue> createSimplifiedLookup(EncodedValueLookup lookup) {
        return key -> {
            if (key.startsWith(BACKWARD_PREFIX))
                return lookup.getEncodedValue(key.substring(BACKWARD_PREFIX.length()), EncodedValue.class);
            else if (key.startsWith(PREV_PREFIX))
                return lookup.getEncodedValue(key.substring(PREV_PREFIX.length()), EncodedValue.class);
            else if (lookup.hasEncodedValue(key))
                return lookup.getEncodedValue(key, EncodedValue.class);
            else return null;
        };
    }

    static void parseExpressions(StringBuilder expressions, NameValidator nameInConditionValidator,
                                 String exceptionInfo, Set<String> createObjects, List<Statement> list,
                                 ClassHelper classHelper, String indentation) {

        for (Statement statement : list) {
            // avoid parsing the RHS value expression again as we just did it to get the maximum values in createClazz
            if (statement.keyword() == Statement.Keyword.ELSE) {
                if (!Helper.isEmpty(statement.condition()))
                    throw new IllegalArgumentException("condition must be empty but was " + statement.condition());

                expressions.append(indentation);
                if (statement.isBlock()) {
                    expressions.append("else {");
                    parseExpressions(expressions, nameInConditionValidator, exceptionInfo, createObjects, statement.doBlock(), classHelper, indentation + "  ");
                    expressions.append(indentation).append("}\n");
                } else {
                    expressions.append("else {").append(statement.operation().build(statement.value())).append("; }\n");
                }
            } else if (statement.keyword() == Statement.Keyword.ELSEIF || statement.keyword() == Statement.Keyword.IF) {
                ParseResult parseResult = ConditionalExpressionVisitor.parse(statement.condition(), nameInConditionValidator, classHelper);
                if (!parseResult.ok)
                    throw new IllegalArgumentException(exceptionInfo + " invalid condition \"" + statement.condition() + "\"" +
                            (parseResult.invalidMessage == null ? "" : ": " + parseResult.invalidMessage));
                createObjects.addAll(parseResult.guessedVariables);
                if (statement.keyword() == Statement.Keyword.ELSEIF)
                    expressions.append(indentation).append("else ");

                expressions.append(indentation);
                if (statement.isBlock()) {
                    expressions.append("if (").append(parseResult.converted).append(") {\n");
                    parseExpressions(expressions, nameInConditionValidator, exceptionInfo, createObjects, statement.doBlock(), classHelper, indentation + "  ");
                    expressions.append(indentation).append("}\n");
                } else {
                    expressions.append("if (").append(parseResult.converted).append(") {").
                            append(statement.operation().build(statement.value())).append(";}\n");
                }
            } else {
                throw new IllegalArgumentException("The statement must be either 'if', 'else_if' or 'else'");
            }
        }
    }

    /**
     * Injects the already parsed expressions (converted to BlockStatement) via Janino's DeepCopier to the provided
     * CompilationUnit cu (a class file).
     */
    private static Java.CompilationUnit injectStatements(List<Java.BlockStatement> priorityStatements,
                                                         List<Java.BlockStatement> speedStatements,
                                                         List<Java.BlockStatement> turnPenaltyStatements,
                                                         Java.CompilationUnit cu) throws CompileException {
        cu = new DeepCopier() {
            boolean speedInjected = false;
            boolean priorityInjected = false;
            boolean turnPenaltyInjected = false;

            @Override
            public Java.MethodDeclarator copyMethodDeclarator(Java.MethodDeclarator subject) throws CompileException {
                if (subject.name.equals("getSpeed") && !speedStatements.isEmpty() && !speedInjected) {
                    speedInjected = true;
                    return injectStatements(subject, this, speedStatements);
                } else if (subject.name.equals("getPriority") && !priorityStatements.isEmpty() && !priorityInjected) {
                    priorityInjected = true;
                    return injectStatements(subject, this, priorityStatements);
                } else if (subject.name.equals("getTurnPenalty") && !turnPenaltyStatements.isEmpty() && !turnPenaltyInjected) {
                    turnPenaltyInjected = true;
                    return injectStatements(subject, this, turnPenaltyStatements);
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
