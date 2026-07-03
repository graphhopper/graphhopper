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
package com.graphhopper.routing.weighting.custom

import com.graphhopper.json.Statement
import com.graphhopper.json.Statement.Keyword.IF
import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EncodedValue
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.IntEncodedValue
import com.graphhopper.routing.ev.Orientation
import com.graphhopper.routing.ev.StringEncodedValue
import com.graphhopper.routing.weighting.TurnCostProvider
import com.graphhopper.storage.BaseGraph
import com.graphhopper.util.CustomModel
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.GHUtility
import com.graphhopper.util.Helper
import com.graphhopper.util.JsonFeature
import com.graphhopper.util.Parameters as GHParameters
import com.graphhopper.util.shapes.BBox
import com.graphhopper.util.shapes.Polygon
import org.codehaus.commons.compiler.CompileException
import org.codehaus.commons.compiler.Location
import org.codehaus.commons.compiler.io.Readers
import org.codehaus.janino.Java
import org.codehaus.janino.Parser
import org.codehaus.janino.Scanner
import org.codehaus.janino.SimpleCompiler
import org.codehaus.janino.Unparser
import org.codehaus.janino.util.DeepCopier
import org.locationtech.jts.geom.Polygonal
import org.locationtech.jts.geom.prep.PreparedPolygon
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

object CustomModelParser {
    private val longVal = AtomicLong(1)
    internal const val IN_AREA_PREFIX = "in_"
    internal const val BACKWARD_PREFIX = "backward_"
    internal const val PREV_PREFIX = "prev_"
    internal const val CHANGE_ANGLE = "change_angle"
    internal const val STREET_NAME = "street_name"
    private val JANINO_DEBUG = java.lang.Boolean.getBoolean(Scanner.SYSTEM_PROPERTY_SOURCE_DEBUGGING_ENABLE)
    private val SCRIPT_FILE_DIR = System.getProperty(Scanner.SYSTEM_PROPERTY_SOURCE_DEBUGGING_DIR, "./src/main/java/com/graphhopper/routing/weighting/custom")

    // Without a cache the class creation takes 10-40ms which makes routingLM8 requests 20% slower on average.
    // CH requests and preparation is unaffected as cached weighting from preparation is used.
    // Use accessOrder==true to remove oldest accessed entry, not oldest inserted.
    private val CACHE_SIZE: Int = Integer.getInteger("graphhopper.custom_weighting.cache_size", 1000)
    private val CACHE: MutableMap<String, Class<*>> = Collections.synchronizedMap(
            object : LinkedHashMap<String, Class<*>>(CACHE_SIZE, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Class<*>>): Boolean {
                    return size > CACHE_SIZE
                }
            })

    // This internal cache ensures that the "internal" Weighting classes specified in the profiles, are never removed regardless
    // of how frequent other Weightings are created and accessed. We only need to synchronize the get and put methods alone.
    // E.g. we do not care for the race condition where two identical classes are requested and one of them is overwritten.
    // TODO perf compare with ConcurrentHashMap, but I guess, if there is a difference at all, it is not big for small maps
    private val INTERNAL_CACHE: MutableMap<String, Class<*>> = Collections.synchronizedMap(HashMap())

    /**
     * This method creates a weighting from a CustomModel that must limit the speed. Either as an
     * unconditional statement <code>{ "if": "true", "limit_to": "car_average_speed" }<code/> or as
     * an if-elseif-else group.
     */
    @JvmStatic
    fun createWeighting(lookup: EncodedValueLookup, turnCostProvider: TurnCostProvider, customModel: CustomModel?): CustomWeighting {
        if (customModel == null)
            throw IllegalStateException("CustomModel cannot be null")

        val parameters = createWeightingParameters(customModel, lookup)
        return CustomWeighting(turnCostProvider, parameters)
    }

    /**
     * This method creates the CustomWeighting.Parameters for the provided CustomModel via the currently
     * registered [CustomWeightingBackends.default] backend (by default the Janino-based [JaninoBackend]).
     */
    @JvmStatic
    fun createWeightingParameters(customModel: CustomModel, lookup: EncodedValueLookup): CustomWeighting.Parameters {
        return CustomWeightingBackends.default.createParameters(customModel, lookup)
    }

    /**
     * This method compiles a new subclass of CustomWeightingHelper composed of the provided CustomModel caches this
     * and returns an instance. This is the [JaninoBackend] implementation - use
     * [createWeightingParameters] to go through the configurable backend seam.
     */
    internal fun createJaninoWeightingParameters(customModel: CustomModel, lookup: EncodedValueLookup): CustomWeighting.Parameters {
        val key = customModel.toString()
        var clazz: Class<*>? = if (customModel.isInternal()) INTERNAL_CACHE[key] else null
        if (CACHE_SIZE > 0 && clazz == null)
            clazz = CACHE[key]
        if (clazz == null) {
            clazz = createClazz(customModel, lookup)
            if (customModel.isInternal()) {
                INTERNAL_CACHE[key] = clazz
                if (INTERNAL_CACHE.size > 100) {
                    CACHE.putAll(INTERNAL_CACHE)
                    INTERNAL_CACHE.clear()
                    LoggerFactory.getLogger(CustomModelParser::class.java).warn("Internal cache must stay small but was "
                            + INTERNAL_CACHE.size + ". Cleared it. Misuse of CustomModel::internal?")
                }
            } else if (CACHE_SIZE > 0) {
                CACHE[key] = clazz
            }
        }

        try {
            // The class does not need to be thread-safe as we create an instance per request
            val prio = clazz.getDeclaredConstructor().newInstance() as CustomWeightingHelper
            prio.init(customModel, lookup, CustomModel.getAreasAsMap(customModel.getAreas()))
            return CustomWeighting.Parameters(
                    prio::getSpeed, prio::calcMaxSpeed,
                    prio::getPriority, prio::calcMaxPriority,
                    prio::getTurnPenalty,
                    customModel.getDistanceInfluence() ?: 0.0,
                    customModel.getHeadingPenalty() ?: GHParameters.Routing.DEFAULT_HEADING_PENALTY)
        } catch (ex: ReflectiveOperationException) {
            throw IllegalArgumentException("Cannot compile expression " + ex.message, ex)
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
    private fun createClazz(customModel: CustomModel, lookup: EncodedValueLookup): Class<*> {
        try {
            val priorityVariables = ValueExpressionVisitor.findVariables(customModel.getPriority(), lookup)
            val priorityStatements = createGetPriorityStatements(priorityVariables, customModel, lookup)

            if (customModel.getSpeed().isEmpty())
                throw IllegalArgumentException("At least one initial statement under 'speed' is required.")

            val firstGroup = splitIntoGroup(customModel.getSpeed())[0]
            if (firstGroup.size > 1) {
                val lastSt = firstGroup[firstGroup.size - 1]
                if (lastSt.operation() != Statement.Op.LIMIT || lastSt.keyword() != Statement.Keyword.ELSE)
                    throw IllegalArgumentException("The first group needs to end with an 'else' (or contain a single unconditional 'if' statement).")
            } else {
                val firstSt = firstGroup[0]
                if ("true" != firstSt.condition() || firstSt.operation() != Statement.Op.LIMIT || firstSt.keyword() != Statement.Keyword.IF)
                    throw IllegalArgumentException("The first group needs to contain a single unconditional 'if' statement (or end with an 'else').")
            }

            val speedVariables = ValueExpressionVisitor.findVariables(customModel.getSpeed(), lookup)
            val speedStatements = createGetSpeedStatements(speedVariables, customModel, lookup)

            val turnPenaltyVariables = ValueExpressionVisitor.findVariables(customModel.getTurnPenalty(), lookup)
            val turnPenaltyStatements = createGetTurnPenaltyStatements(turnPenaltyVariables, customModel, lookup)

            // Create different class name, which is required only for debugging.
            // TODO does it improve performance too? I.e. it could be that the JIT is confused if different classes
            //  have the same name and it mixes performance stats. See https://github.com/janino-compiler/janino/issues/137
            val counter = longVal.incrementAndGet()
            val classTemplate = createClassTemplate(counter, priorityVariables, speedVariables, turnPenaltyVariables, lookup, CustomModel.getAreasAsMap(customModel.getAreas()))
            var cu = Parser(Scanner("source", StringReader(classTemplate))).
                    parseAbstractCompilationUnit() as Java.CompilationUnit
            cu = injectStatements(priorityStatements, speedStatements, turnPenaltyStatements, cu)
            val sc = createCompiler(counter, cu)
            return sc.getClassLoader().loadClass("com.graphhopper.routing.weighting.custom.JaninoCustomWeightingHelperSubclass" + counter)
        } catch (ex: Exception) {
            val errString = "Cannot compile expression"
            throw IllegalArgumentException(errString + ": " + ex.message, ex)
        }
    }

    @JvmStatic
    fun findVariablesForEncodedValuesString(model: CustomModel, nameValidator: NameValidator, classHelper: ClassHelper): List<String> {
        val variables = LinkedHashSet<String>()
        // avoid parsing exception for e.g. in_xy
        val nameValidatorIntern = NameValidator { s ->
            // some literals are no variables and would throw an exception (encoded value not found)
            if (Character.isUpperCase(s[0]) || s.startsWith(IN_AREA_PREFIX))
                true
            else if (nameValidator.isValid(s)) {
                variables.add(s)
                true
            } else {
                false
            }
        }
        findVariablesForEncodedValuesString(model.getPriority(), nameValidatorIntern, classHelper)
        findVariablesForEncodedValuesString(model.getSpeed(), nameValidatorIntern, classHelper)
        return ArrayList(variables)
    }

    private fun findVariablesForEncodedValuesString(statements: List<Statement>, nameValidator: NameValidator, classHelper: ClassHelper) {
        val groups = splitIntoGroup(statements)
        for (group in groups) {
            for (statement in group) {
                if (statement.isBlock()) {
                    findVariablesForEncodedValuesString(statement.doBlock(), nameValidator, classHelper)
                } else {
                    // ignore potential problems; collect only variables in this step
                    ConditionalExpressionVisitor.parse(statement.condition(), nameValidator, classHelper)
                    ValueExpressionVisitor.parse(statement.value(), nameValidator)
                }
            }
        }
    }

    /**
     * Splits the specified list into several lists of statements starting with if.
     * I.e. a group consists of one 'if' and zero or more 'else_if' and 'else' statements.
     */
    internal fun splitIntoGroup(statements: List<Statement>): List<List<Statement>> {
        val result = ArrayList<MutableList<Statement>>()
        var group: MutableList<Statement>? = null
        for (st in statements) {
            if (IF == st.keyword()) {
                group = ArrayList()
                result.add(group)
            }
            if (group == null)
                throw IllegalArgumentException("Every group must start with an if-statement")
            group.add(st)
        }
        return result
    }

    /**
     * Parse the expressions from CustomModel relevant for the method getSpeed - see createClassTemplate.
     *
     * @return the created statements (parsed expressions)
     */
    @Throws(Exception::class)
    private fun createGetSpeedStatements(speedVariables: MutableSet<String>,
                                         customModel: CustomModel, lookup: EncodedValueLookup): List<Java.BlockStatement> {
        val speedStatements = ArrayList(verifyExpressions(StringBuilder(),
                "speed entry", speedVariables, customModel.getSpeed(), lookup))
        var speedMethodStartBlock = "double value = " + CustomWeightingHelper.GLOBAL_MAX_SPEED + ";\n"
        // potentially we fetch EncodedValues twice (one time here and one time for priority)
        for (arg in speedVariables) {
            speedMethodStartBlock += getVariableDeclaration(lookup, arg)
        }
        speedStatements.addAll(0, Parser(Scanner("getSpeed", StringReader(speedMethodStartBlock))).
                parseBlockStatements())
        return speedStatements
    }

    /**
     * Parse the expressions from CustomModel relevant for the method getPriority - see createClassTemplate.
     *
     * @return the created statements (parsed expressions)
     */
    @Throws(Exception::class)
    private fun createGetPriorityStatements(priorityVariables: MutableSet<String>,
                                            customModel: CustomModel, lookup: EncodedValueLookup): List<Java.BlockStatement> {
        for (s in customModel.getPriority()) {
            if (s.operation() == Statement.Op.ADD)
                throw IllegalArgumentException("'priority' statement must not have the operation 'add'")
        }
        val priorityStatements = ArrayList(verifyExpressions(StringBuilder(),
                "priority entry", priorityVariables, customModel.getPriority(), lookup))
        var priorityMethodStartBlock = "double value = " + CustomWeightingHelper.GLOBAL_PRIORITY + ";\n"
        for (arg in priorityVariables) {
            priorityMethodStartBlock += getVariableDeclaration(lookup, arg)
        }
        priorityStatements.addAll(0, Parser(Scanner("getPriority", StringReader(priorityMethodStartBlock))).
                parseBlockStatements())
        return priorityStatements
    }

    /**
     * Parse the expressions from CustomModel relevant for the method getTurnPenalty - see createClassTemplate.
     *
     * @return the created statements (parsed expressions)
     */
    @Throws(Exception::class)
    private fun createGetTurnPenaltyStatements(turnPenaltyVariables: MutableSet<String>,
                                               customModel: CustomModel, lookup: EncodedValueLookup): List<Java.BlockStatement> {
        for (s in customModel.getTurnPenalty()) {
            if (s.operation() == Statement.Op.ADD && s.value().trim().startsWith("-"))
                throw IllegalArgumentException("The value for the 'add' operation must be positive, but was: " + s.value())
            if (s.isBlock())
                throw IllegalArgumentException("'turn_penalty' statement cannot be a block (not yet implemented)")
            if (s.operation() != Statement.Op.ADD)
                throw IllegalArgumentException("'turn_penalty' statement must have the operation 'add' but was: " + s.operation() + " (not yet implemented)")
        }

        val turnPenaltyStatements = ArrayList(verifyExpressions(StringBuilder(),
                "turn_penalty entry", turnPenaltyVariables, customModel.getTurnPenalty(), lookup))
        var needTwoDirections = false
        val fct = createSimplifiedLookup(lookup)
        for (ttv in turnPenaltyVariables) {
            val ev = fct(ttv)
            if (ev != null && ev.isStoreTwoDirections || ttv == CHANGE_ANGLE) {
                needTwoDirections = true
                break
            }
        }

        var turnPenaltyMethodStartBlock = "double value = 0;\n"
        if (needTwoDirections) {
            // Performance optimization: avoid the following two calls if there is no encoded value
            // that stores two directions. The call to isAdjNode is slightly faster than calling
            // getEdgeIteratorState as it avoids creating a new object and accesses only one node
            // but is slightly less safe as it cannot check that at least one node must be
            // identical (the case where getEdgeIteratorState returns null)
            turnPenaltyMethodStartBlock += "boolean inEdgeReverse = !graph.isAdjNode(inEdge, viaNode);\n" +
                    "boolean outEdgeReverse = graph.isAdjNode(outEdge, viaNode);\n"
        }

        for (arg in turnPenaltyVariables) {
            turnPenaltyMethodStartBlock += getTurnPenaltyVariableDeclaration(lookup, arg, needTwoDirections)
        }

        // special case for change_angle method call: we need the orientation encoded value
        if (turnPenaltyVariables.contains(CHANGE_ANGLE)) {
            turnPenaltyVariables.remove(CHANGE_ANGLE)
            turnPenaltyVariables.add(Orientation.KEY)
        }

        turnPenaltyStatements.addAll(0, Parser(Scanner("getTurnPenalty", StringReader(turnPenaltyMethodStartBlock))).
                parseBlockStatements())
        return turnPenaltyStatements
    }

    /**
     * For the methods getSpeed and getPriority we declare variables that contain the encoded value of the current edge
     * or if an area contains the current edge.
     */
    private fun getVariableDeclaration(lookup: EncodedValueLookup, arg: String): String {
        if (lookup.hasEncodedValue(arg)) {
            // parameters in method getPriority or getSpeed are: EdgeIteratorState edge, boolean reverse
            val enc = lookup.getEncodedValue(arg, EncodedValue::class.java)
            return getReturnType(enc) + " " + arg + " = (" + getReturnType(enc) + ") (reverse ? " +
                    "edge.getReverse((" + getInterface(enc) + ") this." + arg + "_enc) : " +
                    "edge.get((" + getInterface(enc) + ") this." + arg + "_enc));\n"
        } else if (arg.startsWith(BACKWARD_PREFIX)) {
            val argSubstr = arg.substring(BACKWARD_PREFIX.length)
            if (lookup.hasEncodedValue(argSubstr)) {
                val enc = lookup.getEncodedValue(argSubstr, EncodedValue::class.java)
                return getReturnType(enc) + " " + arg + " = (" + getReturnType(enc) + ") (reverse ? " +
                        "edge.get((" + getInterface(enc) + ") this." + argSubstr + "_enc) : " +
                        "edge.getReverse((" + getInterface(enc) + ") this." + argSubstr + "_enc));\n"
            } else {
                throw IllegalArgumentException("Not supported for backward: " + argSubstr)
            }
        } else if (arg.startsWith(IN_AREA_PREFIX)) {
            return ""
        } else {
            throw IllegalArgumentException("Not supported " + arg)
        }
    }

    private fun getTurnPenaltyVariableDeclaration(lookup: EncodedValueLookup, arg: String, needTwoDirections: Boolean): String {
        // parameters in method getTurnPenalty are: int inEdge, int viaNode, int outEdge.
        // The variables outEdgeReverse and inEdgeReverse are provided from initial calls if needTwoDirections is true.
        if (arg == CHANGE_ANGLE) {
            // calcChangeAngle expects the orientation slot at the viaNode side of outEdge (see OrientationCalculator);
            // since outEdgeReverse now means direction of travel, invert it here.
            return "double change_angle = CustomWeightingHelper.calcChangeAngle(edgeIntAccess, this.orientation_enc, inEdge, inEdgeReverse, outEdge, !outEdgeReverse);\n"
        } else if (arg == STREET_NAME) {
            return "String street_name = graph.getEdgeIteratorState(outEdge, Integer.MIN_VALUE).getName();\n" // TODO PERF: get ref into KVStorage without creation of EdgeIteratorState
        } else if (arg == PREV_PREFIX + STREET_NAME) {
            return "String prev_street_name = graph.getEdgeIteratorState(inEdge, Integer.MIN_VALUE).getName();\n" // TODO PERF
        } else if (lookup.hasEncodedValue(arg)) {
            val enc = lookup.getEncodedValue(arg, EncodedValue::class.java)
            val reverseExpr = if (needTwoDirections) "outEdgeReverse" else "false"
            return getReturnType(enc) + " " + arg + " = (" + getReturnType(enc) + ") " +
                    getTurnPenaltyAccessor(enc, arg, reverseExpr, "outEdge") + ";\n"
        } else if (arg.startsWith(PREV_PREFIX)) {
            val argSubstr = arg.substring(PREV_PREFIX.length)
            if (lookup.hasEncodedValue(argSubstr)) {
                val enc = lookup.getEncodedValue(argSubstr, EncodedValue::class.java)
                val reverseExpr = if (needTwoDirections) "inEdgeReverse" else "false"
                return getReturnType(enc) + " " + arg + " = (" + getReturnType(enc) + ") " +
                        getTurnPenaltyAccessor(enc, argSubstr, reverseExpr, "inEdge") + ";\n"
            } else {
                throw IllegalArgumentException("Not supported for prev: " + argSubstr)
            }
        } else {
            throw IllegalArgumentException("Not supported for turn_penalty: " + arg)
        }
    }

    /**
     * @return the interface as string of the provided EncodedValue, e.g. IntEncodedValue (only interface) or
     * BooleanEncodedValue (first interface). For StringEncodedValue we return IntEncodedValue to return the index
     * instead of the String for faster comparison.
     */
    private fun getInterface(enc: EncodedValue): String {
        if (enc is StringEncodedValue) return IntEncodedValue::class.java.simpleName
        if (enc.javaClass.interfaces.isEmpty()) return enc.javaClass.simpleName
        return enc.javaClass.interfaces[0].simpleName
    }

    /**
     * @return the accessor method call for the given EncodedValue used in turn penalty code, e.g.
     * "this.road_class_enc.getEnum(reverse, edgeId, edgeIntAccess)" for EnumEncodedValue.
     */
    private fun getTurnPenaltyAccessor(enc: EncodedValue, fieldName: String, reverseExpr: String, edgeExpr: String): String {
        val method: String
        // order is important: EnumEncodedValue and BooleanEncodedValue extend IntEncodedValueImpl
        if (enc is EnumEncodedValue<*>) method = "getEnum"
        else if (enc is BooleanEncodedValue) method = "getBool"
        else if (enc is DecimalEncodedValue) method = "getDecimal"
        else if (enc is IntEncodedValue) method = "getInt"
        else throw IllegalArgumentException("Unsupported EncodedValue for turn penalty: " + enc.javaClass)

        return "this." + fieldName + "_enc." + method + "(" + reverseExpr + ", " + edgeExpr + ", edgeIntAccess)"
    }

    private fun getReturnType(encodedValue: EncodedValue): String {
        // order is important
        if (encodedValue is EnumEncodedValue<*>) {
            val cl = encodedValue.enumType
            // use getSimpleName for inbuilt EncodedValues and more readability of generated source
            return if (cl.getPackage() == EnumEncodedValue::class.java.getPackage()) cl.simpleName else cl.name
        }
        if (encodedValue is StringEncodedValue) return "int" // we use indexOf
        if (encodedValue is DecimalEncodedValue) return "double"
        if (encodedValue is BooleanEncodedValue) return "boolean"
        if (encodedValue is IntEncodedValue) return "int"
        throw IllegalArgumentException("Unsupported EncodedValue: " + encodedValue.javaClass)
    }

    /**
     * Create the class source file from the detected variables (priorityVariables and speedVariables). We assume that
     * these variables are safe although they are user input because we collected them from parsing via Janino. This
     * means that the source file is free from user input and could be directly compiled. Before we do this we still
     * have to inject that parsed and safe user expressions in a later step.
     */
    private fun createClassTemplate(counter: Long,
                                    priorityVariables: Set<String>,
                                    speedVariables: Set<String>,
                                    turnPenaltyVariables: Set<String>,
                                    lookup: EncodedValueLookup, areas: Map<String, JsonFeature>): String {
        val importSourceCode = StringBuilder("import com.graphhopper.routing.ev.*;\n")
        importSourceCode.append("import java.util.Map;\n")
        importSourceCode.append("import " + CustomModel::class.java.name + ";\n")
        importSourceCode.append("import " + BaseGraph::class.java.name + ";\n")
        importSourceCode.append("import " + EdgeIntAccess::class.java.name + ";\n")
        val classSourceCode = StringBuilder(100)
        var includedAreaImports = false

        val initSourceCode = StringBuilder("this.lookup = lookup;\n")
        initSourceCode.append("this.customModel = customModel;\n")
        val set = HashSet<String>()
        for (prioVar in priorityVariables)
            set.add(if (prioVar.startsWith(BACKWARD_PREFIX)) prioVar.substring(BACKWARD_PREFIX.length) else prioVar)
        for (speedVar in speedVariables)
            set.add(if (speedVar.startsWith(BACKWARD_PREFIX)) speedVar.substring(BACKWARD_PREFIX.length) else speedVar)
        for (speedVar in turnPenaltyVariables)
            set.add(if (speedVar.startsWith(PREV_PREFIX)) speedVar.substring(PREV_PREFIX.length) else speedVar)

        for (arg in set) {
            if (lookup.hasEncodedValue(arg)) {
                val enc = lookup.getEncodedValue(arg, EncodedValue::class.java)
                classSourceCode.append("protected " + getInterface(enc) + " " + arg + "_enc;\n")
                initSourceCode.append("this." + arg + "_enc = (" + getInterface(enc)
                        + ") lookup.getEncodedValue(\"" + arg + "\", EncodedValue.class);\n")
            } else if (arg.startsWith(IN_AREA_PREFIX)) {
                if (!includedAreaImports) {
                    importSourceCode.append("import " + BBox::class.java.name + ";\n")
                    importSourceCode.append("import " + GHUtility::class.java.name + ";\n")
                    importSourceCode.append("import " + PreparedPolygon::class.java.name + ";\n")
                    importSourceCode.append("import " + Polygonal::class.java.name + ";\n")
                    importSourceCode.append("import " + JsonFeature::class.java.name + ";\n")
                    importSourceCode.append("import " + Polygon::class.java.name + ";\n")
                    includedAreaImports = true
                }

                if (!JsonFeature.isValidId(arg))
                    throw IllegalArgumentException("Area has invalid name: " + arg)
                val id = arg.substring(IN_AREA_PREFIX.length)
                val feature = areas[id]
                        ?: throw IllegalArgumentException("Area '" + id + "' wasn't found")
                if (feature.getGeometry() == null)
                    throw IllegalArgumentException("Area '" + id + "' does not contain a geometry")
                if (feature.getGeometry() !is Polygonal)
                    throw IllegalArgumentException("Currently only type=Polygon is supported for areas but was " + feature.getGeometry().getGeometryType())
                if (feature.getBBox() != null)
                    throw IllegalArgumentException("Bounding box of area " + id + " must be empty")
                classSourceCode.append("protected " + Polygon::class.java.simpleName + " " + arg + ";\n")
                initSourceCode.append("JsonFeature feature_" + id + " = (JsonFeature) areas.get(\"" + id + "\");\n")
                initSourceCode.append("this." + arg + " = new Polygon(new PreparedPolygon((Polygonal) feature_" + id + ".getGeometry()));\n")
            } else if (arg == STREET_NAME) {
                // street_name is resolved at runtime from graph KV storage, no class field needed
            } else {
                if (!arg.startsWith(IN_AREA_PREFIX))
                    throw IllegalArgumentException("Variable not supported: " + arg)
            }
        }

        return ("" +
                "package com.graphhopper.routing.weighting.custom;\n" +
                "import " + CustomWeightingHelper::class.java.name + ";\n" +
                "import " + EncodedValueLookup::class.java.name + ";\n" +
                "import " + EdgeIteratorState::class.java.name + ";\n" +
                importSourceCode +
                "\npublic class JaninoCustomWeightingHelperSubclass" + counter + " extends " + CustomWeightingHelper::class.java.simpleName + " {\n" +
                classSourceCode +
                "   @Override\n" +
                "   public void init(CustomModel customModel, EncodedValueLookup lookup, Map<String, " + JsonFeature::class.java.name + "> areas) {\n" +
                initSourceCode +
                "   }\n\n" +
                // we need these placeholder methods so that the hooks in DeepCopier are invoked
                "   @Override\n" +
                "   public double getPriority(EdgeIteratorState edge, boolean reverse) {\n" +
                "      return 1; //will be overwritten by code injected in DeepCopier\n" +
                "   }\n" +
                "   @Override\n" +
                "   public double getSpeed(EdgeIteratorState edge, boolean reverse) {\n" +
                "      return 1; //will be overwritten by code injected in DeepCopier\n" +
                "   }\n" +
                "   @Override\n" +
                "   public double getTurnPenalty(BaseGraph graph, EdgeIntAccess edgeIntAccess, int inEdge, int viaNode, int outEdge) {\n" +
                "      return 1; //will be overwritten by code injected in DeepCopier\n" +
                "   }\n" +
                "}")
    }

    /**
     * This method does:
     * 1. check user expressions via Parser.parseConditionalExpression and only allow whitelisted variables and methods.
     * 2. while this check it also guesses the variable names and stores it in createObjects
     * 3. creates if-then-elseif expressions from the checks and returns them as BlockStatements
     *
     * @return the created if-then, else and elseif statements
     */
    @Throws(Exception::class)
    private fun verifyExpressions(expressions: StringBuilder, info: String, createObjects: MutableSet<String>,
                                  list: List<Statement>, lookup: EncodedValueLookup): List<Java.BlockStatement> {
        // allow variables, all encoded values, constants and special variables like in_xyarea or backward_car_access
        val nameInConditionValidator = NameValidator { name ->
            lookup.hasEncodedValue(name)
                    || name.uppercase(Locale.ROOT) == name || name.startsWith(IN_AREA_PREFIX) || name == CHANGE_ANGLE
                    || name == STREET_NAME || name == PREV_PREFIX + STREET_NAME
                    || name.startsWith(BACKWARD_PREFIX) && lookup.hasEncodedValue(name.substring(BACKWARD_PREFIX.length))
                    || name.startsWith(PREV_PREFIX) && lookup.hasEncodedValue(name.substring(PREV_PREFIX.length))
        }
        val fct = createSimplifiedLookup(lookup)
        val helper = ClassHelper { key ->
            val ev = fct(key) ?: throw IllegalArgumentException("Couldn't find class for " + key)
            getReturnType(ev)
        }

        parseExpressions(expressions, nameInConditionValidator, info, createObjects, list, helper, "")
        expressions.append("return value;\n")
        return Parser(Scanner(info, StringReader(expressions.toString()))).
                parseBlockStatements()
    }

    private fun createSimplifiedLookup(lookup: EncodedValueLookup): (String) -> EncodedValue? {
        return { key ->
            if (key == STREET_NAME || key == PREV_PREFIX + STREET_NAME)
                null
            else if (key.startsWith(BACKWARD_PREFIX))
                lookup.getEncodedValue(key.substring(BACKWARD_PREFIX.length), EncodedValue::class.java)
            else if (key.startsWith(PREV_PREFIX))
                lookup.getEncodedValue(key.substring(PREV_PREFIX.length), EncodedValue::class.java)
            else if (lookup.hasEncodedValue(key))
                lookup.getEncodedValue(key, EncodedValue::class.java)
            else null
        }
    }

    @JvmStatic
    @JvmName("parseExpressions")
    internal fun parseExpressions(expressions: StringBuilder, nameInConditionValidator: NameValidator,
                                  exceptionInfo: String, createObjects: MutableSet<String>, list: List<Statement>,
                                  classHelper: ClassHelper, indentation: String) {

        for (statement in list) {
            // avoid parsing the RHS value expression again as we just did it to get the maximum values in createClazz
            if (statement.keyword() == Statement.Keyword.ELSE) {
                if (!Helper.isEmpty(statement.condition()))
                    throw IllegalArgumentException("condition must be empty but was " + statement.condition())

                expressions.append(indentation)
                if (statement.isBlock()) {
                    expressions.append("else {")
                    parseExpressions(expressions, nameInConditionValidator, exceptionInfo, createObjects, statement.doBlock(), classHelper, indentation + "  ")
                    expressions.append(indentation).append("}\n")
                } else {
                    expressions.append("else {").append(statement.operation().build(statement.value())).append("; }\n")
                }
            } else if (statement.keyword() == Statement.Keyword.ELSEIF || statement.keyword() == Statement.Keyword.IF) {
                val parseResult = ConditionalExpressionVisitor.parse(statement.condition(), nameInConditionValidator, classHelper)
                if (!parseResult.ok)
                    throw IllegalArgumentException(exceptionInfo + " invalid condition \"" + statement.condition() + "\"" +
                            (if (parseResult.invalidMessage == null) "" else ": " + parseResult.invalidMessage))
                createObjects.addAll(parseResult.guessedVariables!!)
                if (statement.keyword() == Statement.Keyword.ELSEIF)
                    expressions.append(indentation).append("else ")

                expressions.append(indentation)
                if (statement.isBlock()) {
                    expressions.append("if (").append(parseResult.converted).append(") {\n")
                    parseExpressions(expressions, nameInConditionValidator, exceptionInfo, createObjects, statement.doBlock(), classHelper, indentation + "  ")
                    expressions.append(indentation).append("}\n")
                } else {
                    expressions.append("if (").append(parseResult.converted).append(") {").
                            append(statement.operation().build(statement.value())).append(";}\n")
                }
            } else {
                throw IllegalArgumentException("The statement must be either 'if', 'else_if' or 'else'")
            }
        }
    }

    /**
     * Injects the already parsed expressions (converted to BlockStatement) via Janino's DeepCopier to the provided
     * CompilationUnit cu (a class file).
     */
    @Throws(CompileException::class)
    private fun injectStatements(priorityStatements: List<Java.BlockStatement>,
                                 speedStatements: List<Java.BlockStatement>,
                                 turnPenaltyStatements: List<Java.BlockStatement>,
                                 cu: Java.CompilationUnit): Java.CompilationUnit {
        return object : DeepCopier() {
            var speedInjected = false
            var priorityInjected = false
            var turnPenaltyInjected = false

            @Throws(CompileException::class)
            override fun copyMethodDeclarator(subject: Java.MethodDeclarator): Java.MethodDeclarator {
                if (subject.name == "getSpeed" && !speedStatements.isEmpty() && !speedInjected) {
                    speedInjected = true
                    return injectStatements(subject, this, speedStatements)
                } else if (subject.name == "getPriority" && !priorityStatements.isEmpty() && !priorityInjected) {
                    priorityInjected = true
                    return injectStatements(subject, this, priorityStatements)
                } else if (subject.name == "getTurnPenalty" && !turnPenaltyStatements.isEmpty() && !turnPenaltyInjected) {
                    turnPenaltyInjected = true
                    return injectStatements(subject, this, turnPenaltyStatements)
                } else {
                    return super.copyMethodDeclarator(subject)
                }
            }
        }.copyCompilationUnit(cu)
    }

    private fun injectStatements(subject: Java.MethodDeclarator, deepCopier: DeepCopier,
                                 statements: List<Java.BlockStatement>): Java.MethodDeclarator {
        try {
            if (statements.isEmpty())
                throw IllegalArgumentException("Statements cannot be empty when copying method")
            val methodDecl = Java.MethodDeclarator(
                    Location("m1", 1, 1),
                    subject.getDocComment(),
                    deepCopier.copyModifiers(subject.getModifiers()),
                    deepCopier.copyOptionalTypeParameters(subject.typeParameters),
                    deepCopier.copyType(subject.type),
                    subject.name,
                    deepCopier.copyFormalParameters(subject.formalParameters),
                    deepCopier.copyTypes(subject.thrownExceptions),
                    deepCopier.copyOptionalElementValue(subject.defaultValue),
                    deepCopier.copyOptionalStatements(statements)
            )
            statements.forEach { st -> st.setEnclosingScope(methodDecl) }
            return methodDecl
        } catch (ex: Exception) {
            throw RuntimeException(ex)
        }
    }

    @Throws(CompileException::class)
    private fun createCompiler(counter: Long, cu: Java.AbstractCompilationUnit): SimpleCompiler {
        if (JANINO_DEBUG) {
            try {
                val sw = StringWriter()
                Unparser.unparse(cu, sw)
                // System.out.println(sw.toString());
                val dir = File(SCRIPT_FILE_DIR)
                val temporaryFile = File(dir, "JaninoCustomWeightingHelperSubclass" + counter + ".java")
                val reader: Reader = Readers.teeReader(
                        StringReader(sw.toString()), // in
                        FileWriter(temporaryFile),   // out
                        true               // closeWriterOnEoi
                )
                return SimpleCompiler(temporaryFile.getAbsolutePath(), reader)
            } catch (ex: Exception) {
                throw RuntimeException(ex)
            }
        } else {
            val compiler = SimpleCompiler()
            // compiler.setWarningHandler((handle, message, location) -> System.out.println(handle + ", " + message + ", " + location));
            compiler.cook(cu)
            return compiler
        }
    }
}
