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

import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.RouteNetwork;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.Polygon;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.Location;
import org.codehaus.commons.compiler.io.Readers;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.*;
import org.codehaus.janino.util.DeepCopier;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

class ExpressionBuilder {
    static final AtomicLong longVal = new AtomicLong(1);
    static final String AREA_PREFIX = "in_area_";
    static final int CACHE_SIZE = Integer.getInteger("com.graphhopper.routing.weighting.custom.cache_size", 500)
            * Runtime.getRuntime().availableProcessors();

    // Note: we only need to synchronize the get and put methods alone. No need to synch the iteration or the block
    // where more work would be needed. E.g. we do not care for the race condition where two identical classes are created
    // and only one is cached. We could use CachingJavaSourceClassLoader but 1. we would need to use a single compiler
    // across threads and 2. the statements (for priority and speed) are still unnecessarily created.
    static final Map<String, Class> cache = Collections.synchronizedMap(new LinkedHashMap<String, Class>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > CACHE_SIZE;
        }
    });

    /**
     * This method compiles a new subclass of CustomWeightingHelper composed from the provided CustomModel caches this
     * and returns an instance.
     */
    static CustomWeightingHelper create(CustomModel customModel, EncodedValueLookup lookup,
                                        double globalMaxSpeed, double maxSpeedFallback, DecimalEncodedValue avgSpeedEnc) {
        Java.CompilationUnit cu;
        try {
            String key = customModel.toString() + ",global:" + globalMaxSpeed + ",fallback:" + maxSpeedFallback;
            if (key.length() > 400_000) throw new IllegalArgumentException("Custom Model too big: " + key.length());
            Class clazz = cache.get(key);
            if (clazz == null) {
                HashSet<String> priorityVariables = new LinkedHashSet<>();
                List<Java.BlockStatement> priorityStatements = createGetPriorityStatements(priorityVariables, customModel, lookup);
                HashSet<String> speedVariables = new LinkedHashSet<>();
                List<Java.BlockStatement> speedStatements = createGetSpeedStatements(speedVariables, customModel, lookup, globalMaxSpeed, maxSpeedFallback);
                // Create different class name, which is required only for debugging.
                // TODO does it improve performance? I.e. will the JIT be confused if different classes have the same name and it mixes performance stats?
                long counter = longVal.incrementAndGet();
                String classTemplate = createClassTemplate(counter, priorityVariables, speedVariables, lookup);
                cu = (Java.CompilationUnit) new Parser(new org.codehaus.janino.Scanner("source", new StringReader(classTemplate))).
                        parseAbstractCompilationUnit();
                cu = injectStatements(priorityStatements, speedStatements, cu);
                SimpleCompiler sc = createCompiler(counter, cu);
                clazz = sc.getClassLoader().loadClass("com.graphhopper.Test" + counter);
                cache.put(key, clazz);
            }

            // The class does not need to be thread-safe as we create an instance per request
            CustomWeightingHelper prio = (CustomWeightingHelper) clazz.getDeclaredConstructor().newInstance();
            prio.init(lookup, avgSpeedEnc, customModel.getAreas());
            return prio;
        } catch (Exception ex) {
            String location = "";
            if (ex instanceof CompileException) {
                location = " in " + ((CompileException) ex).getLocation().getFileName();
                // ex.printStackTrace();
            }
            throw new IllegalArgumentException("Cannot compile expression " + location + ", " + ex.getMessage(), ex);
        }
    }

    /**
     * Parse the expressions from CustomModel relevant for the method getSpeed - see createClassTemplate.
     *
     * @return the created statements (parsed expressions)
     */
    private static List<Java.BlockStatement> createGetSpeedStatements(Set<String> speedVariables,
                                                                      CustomModel customModel, EncodedValueLookup lookup,
                                                                      double globalMaxSpeed, double maxSpeedFallback) throws Exception {
        List<Java.BlockStatement> speedStatements = new ArrayList<>();
        speedStatements.addAll(verifyExpressions(new StringBuilder(), "speed_factor_user_statements", speedVariables,
                customModel.getSpeedFactor(), lookup,
                num -> "speed *= " + num + ";\n", ""));
        StringBuilder codeSB = new StringBuilder("boolean applied = false;\n");
        speedStatements.addAll(verifyExpressions(codeSB, "max_speed_user_statements",
                speedVariables, customModel.getMaxSpeed(), lookup,
                num -> "applied = true; speed = Math.min(speed," + num + ");",
                "if (!applied && speed > " + maxSpeedFallback + ") return " + maxSpeedFallback + ";\n" +
                        "return Math.min(speed, " + globalMaxSpeed + ");\n"));
        String speedMethodStartBlock = "double speed = super.getRawSpeed(edge, reverse);\n";
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
        priorityStatements.addAll(verifyExpressions(new StringBuilder("double value = 1;\n"), "priority_user_statements",
                priorityVariables, customModel.getPriority(), lookup,
                num -> "value *= " + num + ";\n", "return value;"));
        String priorityMethodStartBlock = "";
        for (String arg : priorityVariables) {
            priorityMethodStartBlock += getVariableDeclaration(lookup, arg);
        }
        priorityStatements.addAll(0, new Parser(new org.codehaus.janino.Scanner("getPriority", new StringReader(priorityMethodStartBlock))).
                parseBlockStatements());
        return priorityStatements;
    }

    private static final Set<String> allowedNames = new HashSet<>(Arrays.asList("edge", "Math"));

    static boolean isValidVariableName(String name) {
        return name.startsWith(AREA_PREFIX) || allowedNames.contains(name);
    }

    /**
     * For the methods getSpeed and getPriority we declare variables that contain the encoded value of the current edge
     * or if an area contains the current edge.
     */
    private static String getVariableDeclaration(EncodedValueLookup lookup, String arg) {
        if (arg.startsWith(AREA_PREFIX)) {
            String id = arg.substring(AREA_PREFIX.length());
            return "boolean " + arg + " = " + CustomWeightingHelper.class.getSimpleName() + ".in(this.area_" + id + ", edge);\n";
        } else if (lookup.hasEncodedValue(arg)) {
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
     * @return the interface as string of the provided EncodedValue, e.g. IntEncodedValue (only interface) or BooleanEncodedValue (first interface)
     */
    private static String getInterface(EncodedValue enc) {
        if (enc.getClass().getInterfaces().length == 0) return enc.getClass().getSimpleName();
        return enc.getClass().getInterfaces()[0].getSimpleName();
    }

    private static String getReturnType(EncodedValue encodedValue) {
        String name = encodedValue.getClass().getSimpleName();
        if (name.contains("Enum")) return "Enum";
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
    private static String createClassTemplate(long counter, Set<String> priorityVariables, Set<String> speedVariables, EncodedValueLookup lookup) {
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
                if (!EncodingManager.isSharedEV(enc))
                    continue;
                classSourceCode.append("protected " + enc.getClass().getSimpleName() + " " + arg + "_enc;\n");
                initSourceCode.append("if (lookup.hasEncodedValue(\"" + arg + "\")) ");
                initSourceCode.append("this." + arg + "_enc = (" + enc.getClass().getSimpleName()
                        + ") lookup.getEncodedValue(\"" + arg + "\", EncodedValue.class);\n");
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
                initSourceCode.append("this." + varName + " = new Polygon(new PreparedGeometryFactory().create(feature.getGeometry()));\n");
            } else {
                if (!isValidVariableName(arg))
                    throw new IllegalArgumentException("Variable not supported: " + arg);
            }
        }

        return ""
                + "package com.graphhopper;"
                + "import " + CustomWeightingHelper.class.getName() + ";\n"
                + "import " + EncodedValueLookup.class.getName() + ";\n"
                + "import " + EdgeIteratorState.class.getName() + ";\n"
                + importSourceCode
                + "\npublic class Test" + counter + " extends " + CustomWeightingHelper.class.getSimpleName() + " {\n"
                + classSourceCode
                + "   @Override\n"
                + "   public void init(EncodedValueLookup lookup, "
                + DecimalEncodedValue.class.getName() + " avgSpeedEnc, Map<String, com.graphhopper.json.geo.JsonFeature> areas) {\n"
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
     * @return the created if-then (and elseif) expressions
     */
    private static List<Java.BlockStatement> verifyExpressions(StringBuilder expressions, String info, Set<String> createObjects,
                                                               Map<String, Object> map, EncodedValueLookup lookup,
                                                               CodeBuilder codeBuilder, String lastStmt) throws Exception {
        // allow variables, all encoded values, constants
        ExpressionVisitor.NameValidator nameInConditionValidator = name -> lookup.hasEncodedValue(name)
                || name.toUpperCase(Locale.ROOT).equals(name) || isValidVariableName(name);
        ExpressionVisitor.parseExpressions(expressions, nameInConditionValidator, info, createObjects, map, codeBuilder, lastStmt, false);
        return new Parser(new org.codehaus.janino.Scanner(info, new StringReader(expressions.toString()))).
                parseBlockStatements();
    }

    interface CodeBuilder {
        String create(Number n);
    }

    /**
     * Injects the already parsed expressions (converted to BlockStatement) via janinos DeepCopier to the provided
     * CompilationUnit cu (a class file).
     */
    private static Java.CompilationUnit injectStatements(List<Java.BlockStatement> priorityStatements,
                                                         List<Java.BlockStatement> speedStatements,
                                                         Java.CompilationUnit cu) throws CompileException {
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
                    return injectStatements(subject, this, speedStatements);
                } else if (subject.name.equals("getPriority")) {
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
            statements.clear();
            return methodDecl;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static SimpleCompiler createCompiler(long counter, Java.AbstractCompilationUnit cu) throws CompileException {
        if (Boolean.getBoolean(Scanner.SYSTEM_PROPERTY_SOURCE_DEBUGGING_ENABLE)) {
            try {
                StringWriter sw = new StringWriter();
                Unparser.unparse(cu, sw);
                // System.out.println(sw.toString());
                File dir = new File("./src/main/java/com/graphhopper");
                File temporaryFile = new File(dir, "Test" + counter + ".java");
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
