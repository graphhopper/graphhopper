package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.Location;
import org.codehaus.janino.*;
import org.codehaus.janino.util.DeepCopier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static com.graphhopper.routing.ev.RoadClass.*;
import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static org.junit.jupiter.api.Assertions.*;

// TODO NOW copy entire CustomWeightingTest
class ScriptWeightingTest {

    GraphHopperStorage graphHopperStorage;
    DecimalEncodedValue avSpeedEnc;
    DecimalEncodedValue maxSpeedEnc;
    EnumEncodedValue<RoadClass> roadClassEnc;
    EncodingManager encodingManager;
    FlagEncoder carFE;

    @BeforeEach
    public void setup() {
        carFE = new CarFlagEncoder().setSpeedTwoDirections(true);
        encodingManager = new EncodingManager.Builder().add(carFE).build();
        avSpeedEnc = carFE.getAverageSpeedEnc();
        maxSpeedEnc = encodingManager.getDecimalEncodedValue(MaxSpeed.KEY);
        roadClassEnc = encodingManager.getEnumEncodedValue(KEY, RoadClass.class);
        graphHopperStorage = new GraphBuilder(encodingManager).create();
    }

    @Test
    public void testBasics() {
        EdgeIteratorState primary = graphHopperStorage.edge(0, 1, 10, true).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);
        EdgeIteratorState secondary = graphHopperStorage.edge(1, 2, 10, true).
                set(roadClassEnc, SECONDARY).set(avSpeedEnc, 70);

        CustomModel vehicleModel = new CustomModel();
        vehicleModel.getPriority().put("road_class == PRIMARY", 1.0);
        vehicleModel.getPriority().put("*", 0.5);

        assertEquals(1.15, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
        assertEquals(1.73, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);

        vehicleModel = new CustomModel();
        vehicleModel.getPriority().put("road_class != PRIMARY", 0.5);
        assertEquals(1.15, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
        assertEquals(1.73, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);

        // change priority for primary explicitly and change priority for secondary using catch all
        vehicleModel.getPriority().put("road_class == SECONDARY", 0.7);
        vehicleModel.getPriority().put(CustomWeighting.CATCH_ALL, 0.9);
        assertEquals(1.2, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
        assertEquals(1.73, createWeighting(vehicleModel).calcEdgeWeight(secondary, false), 0.01);

        // force integer value
        vehicleModel = new CustomModel();
        vehicleModel.getPriority().put("road_class == PRIMARY", 1);
        assertEquals(1.15, createWeighting(vehicleModel).calcEdgeWeight(primary, false), 0.01);
    }

    private Weighting createWeighting(CustomModel vehicleModel) {
        return new ScriptWeighting(carFE, encodingManager, NO_TURN_COST_PROVIDER, vehicleModel);
    }

    @Test
    public void isValidAndSimpleExpression() {
        HashSet<String> set = new HashSet<>();
        ScriptWeighting.NameValidator allNamesValid = s -> false;
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "new Object()", allNamesValid));
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "java.lang.Object", allNamesValid));
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "new Object(){}.toString().length", allNamesValid));
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "Object.class", allNamesValid));
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "System.out.println(\"\")", allNamesValid));
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "something.newInstance()", allNamesValid));
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "e.getClass ( )", allNamesValid));
        assertEquals("[]", set.toString());
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "edge; getClass()", allNamesValid));
        set.clear();
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "edge . getClass()", allNamesValid));
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "(edge = edge) == edge", allNamesValid));
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd" +
                "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd", allNamesValid));
        assertEquals("[]", set.toString());

        ScriptWeighting.NameValidator nameValidator1 = s -> s.equals("edge") || s.equals("PRIMARY") || s.equals("road_class");
        assertTrue(ScriptWeighting.parseAndGuessParameters(set, "edge == edge", nameValidator1));
        assertEquals("[edge]", set.toString());
        assertTrue(ScriptWeighting.parseAndGuessParameters(set, "edge.getDistance()", nameValidator1));
        assertEquals("[edge]", set.toString());
        assertTrue(ScriptWeighting.parseAndGuessParameters(set, "road_class == PRIMARY", nameValidator1));
        assertEquals("[edge, road_class]", set.toString());
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "road_class == PRIMARY", allNamesValid));
        assertTrue(ScriptWeighting.parseAndGuessParameters(set, "road_class.ordinal()*2 == PRIMARY.ordinal()*2", nameValidator1));
        assertTrue(ScriptWeighting.parseAndGuessParameters(set, "Math.sqrt(road_class.ordinal()) > 1", nameValidator1));
    }

    @Test
    public void testBasicsWithCompiler() throws Exception {
        EdgeIteratorState primary = graphHopperStorage.edge(0, 1, 10, true).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);
        EdgeIteratorState secondary = graphHopperStorage.edge(1, 2, 10, true).
                set(roadClassEnc, SECONDARY).set(avSpeedEnc, 70);

        ExpressionEvaluator ee = new ExpressionEvaluator();
        ee.setClassName("GScript");
        ee.setDefaultImports("static com.graphhopper.routing.ev.RoadClass.*");
        ee.setOverrideMethod(new boolean[]{
                true,
        });
        ee.setStaticMethod(new boolean[]{
                false,
        });
        ee.setExpressionTypes(new Class[]{
                double.class,
        });
        ee.setExtendedClass(BaseClass.class);
        ee.setMethodNames(new String[]{
                "getValue",
        });
        ee.setParameters(new String[][]{
                {"edge", "reverse"},
        }, new Class[][]{
                {EdgeIteratorState.class, boolean.class},
        });

        ee.cook(new String[]{
                "e(road_class) == PRIMARY ? 0.7 : 1".replaceAll("e\\(", "edge.get("),
        });

        // init
        BaseClass.road_class = roadClassEnc;

        // per request
        EdgeToValueEntry instance = (EdgeToValueEntry) ee.getClazz().getDeclaredConstructor().newInstance();
        assertEquals(0.7, instance.getValue(primary, false));

        EdgeToValueEntry instance2 = (EdgeToValueEntry) ee.getClazz().getDeclaredConstructor().newInstance();
        assertEquals(1.0, instance2.getValue(secondary, false));
    }

    @Test
    public void compilationUnit() throws Exception {
        EdgeIteratorState primary = graphHopperStorage.edge(0, 1, 10, true).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);

        // try:
        // 1. create template with possibility to overwrite getValue via the user expression
        // 2. create template with user expression and add variables
        Java.AbstractCompilationUnit cu = new Parser(new Scanner("ignore", new StringReader(
                ""
                        + "import " + PriorityScript.class.getName() + ";"
                        + "import " + EncodedValueLookup.class.getName() + ";"
                        + "import " + EnumEncodedValue.class.getName() + ";"
                        + "import " + EdgeIteratorState.class.getName() + ";"
                        + "import " + RoadClass.class.getName() + ";"
                        + "import static " + RoadClass.class.getName() + ".*;"
                        + "public class Test extends PriorityScript {"
                        + "   int i = 5;"
                        + "   @Override "
                        + "   public void init(EncodedValueLookup lookup) {"
                        + "      i = 17;"
                        + "      road_class = lookup.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);"
                        + "   }"
                        + "   @Override "
                        + "   public double getValue(EdgeIteratorState edge, boolean reverse) {"
                        + "      return i; // edge.get(road_class).ordinal();\n"
                        + "   }"
                        + "}"
        ))).parseAbstractCompilationUnit();
        cu = new DeepCopier() {
            public Java.TypeDeclaration
            copyPackageMemberClassDeclaration(Java.PackageMemberClassDeclaration pmcd) throws CompileException {
                Java.AbstractClassDeclaration result = (Java.AbstractClassDeclaration) super.copyPackageMemberClassDeclaration(pmcd);

                result.addFieldDeclaration(new Java.FieldDeclaration(
                        Location.NOWHERE,
                        null,
                        new Java.Modifier[0],
                        new Java.ReferenceType(Location.NOWHERE, new Java.Annotation[0], new String[]{"EnumEncodedValue"}, null),
                        new Java.VariableDeclarator[]{new Java.VariableDeclarator(Location.NOWHERE, "road_class", 0, null)}));
                return result;
            }

//            @Override
//            public Java.MethodDeclarator copyMethodDeclarator(Java.MethodDeclarator subject) throws CompileException {
//                System.out.println("copyMethodDeclarator " + subject.name);
//                if (subject.name.equals("getValue")) {
//                    subject.statements.clear();
//                    try {
//                        Parser parser = new Parser(new Scanner("ignore", new StringReader("return 0.1;")));
//                        Java.BlockStatement s = new Java.ReturnStatement(Location.NOWHERE, parser.parseConditionalExpression().toRvalueOrCompileException());
//                        return new Java.MethodDeclarator(
//                                subject.getLocation(),
//                                subject.getDocComment(),
//                                this.copyModifiers(subject.getModifiers()),
//                                this.copyOptionalTypeParameters(subject.typeParameters),
//                                this.copyType(subject.type),
//                                subject.name,
//                                this.copyFormalParameters(subject.formalParameters),
//                                this.copyTypes(subject.thrownExceptions),
//                                this.copyOptionalElementValue(subject.defaultValue),
//                                this.copyOptionalStatements(Collections.singletonList(s))
//                        );
//                    } catch (Exception ex) {
//                        throw new RuntimeException(ex);
//                    }
//                }
//                return super.copyMethodDeclarator(subject);
//            }
        }.copyAbstractCompilationUnit(cu);
        StringWriter sw = new StringWriter();
        Unparser.unparse(cu, sw);
        System.out.println(sw.toString());

        SimpleCompiler sc = new SimpleCompiler();
        sc.cook(sw.toString());
        PriorityScript prio = (PriorityScript) sc.getClassLoader().
                loadClass("Test").getDeclaredConstructor().newInstance();
        prio.init(encodingManager);
        assertEquals(17, prio.getValue(primary, false));

        prio = (PriorityScript) sc.getClassLoader().
                loadClass("Test").getDeclaredConstructor().newInstance();
        assertEquals(5, prio.getValue(primary, false));
    }

    @Test
    public void testCBE() throws Exception {
        EdgeIteratorState primary = graphHopperStorage.edge(0, 1, 10, true).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);

        ClassBodyEvaluator evaluator = new ClassBodyEvaluator();
        evaluator.setExtendedType(PriorityScript.class);
        evaluator.setClassName("Test");
        evaluator.setDefaultImports("com.graphhopper.routing.weighting.custom.*",
                "com.graphhopper.routing.ev.*",
                "com.graphhopper.util.EdgeIteratorState");
        evaluator.cook(""
                + "protected EnumEncodedValue road_class_enc_2;"
                + "public void init(EncodedValueLookup lookup) {"
                + "   road_class_enc_2 = lookup.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);"
                + "}"
                + "public double getValue(EdgeIteratorState edge, boolean reverse) {"
                + "   return edge.get(road_class_enc_2).ordinal();"
                + "}");
        PriorityScript entry = (PriorityScript) evaluator.getClazz().getDeclaredConstructor().newInstance();
        entry.init(encodingManager);
        // assertTrue(entry instanceof EdgeToValueEntry);
        assertEquals(PRIMARY.ordinal(), entry.getValue(primary, false));
    }

    public void test() throws Exception {
        // Parse the input CU.
        Java.AbstractCompilationUnit cu = new Parser(new Scanner(null, new StringReader(
                ""
                        + " class A {"
                        + " "
                        + "     void test() {"
                        + "         int b = 0;"
                        + "         int c = 1;"
                        + "         b += c;"
                        + "     }"
                        + " "
                        + "     int a;"
                        + " }"
        ))).parseAbstractCompilationUnit();

        // Now copy the input CU and modify it on-the-fly.
        cu = new DeepCopier() {

            private final List<Java.FieldDeclaration> moreFieldDeclarations = new ArrayList<>();

            @Override
            public Java.BlockStatement
            copyLocalVariableDeclarationStatement(Java.LocalVariableDeclarationStatement lvds) throws CompileException {

                /**
                 * Generate synthetic fields for each local variable.
                 */
                List<Java.VariableDeclarator> fieldVariableDeclarators = new ArrayList<>();
                for (Java.VariableDeclarator vd : lvds.variableDeclarators) {
                    fieldVariableDeclarators.add(new Java.VariableDeclarator(
                            vd.getLocation(),
                            vd.name,
                            vd.brackets,
                            null // initializer <= Do NOT copy the initializer!
                    ));
                }
                this.moreFieldDeclarations.add(new Java.FieldDeclaration(
                        Location.NOWHERE,                 // location
                        null,                             // docComment
                        lvds.modifiers,                   // modifiers
                        this.copyType(lvds.type),         // type
                        fieldVariableDeclarators.toArray( // variableDeclarators
                                new Java.VariableDeclarator[fieldVariableDeclarators.size()]
                        )
                ));

                /**
                 * Replace each local variable declaration with an assignment expression statement.
                 */
                List<Java.BlockStatement> assignments = new ArrayList<>();
                for (Java.VariableDeclarator vd : lvds.variableDeclarators) {

                    Java.Rvalue initializer = (Java.Rvalue) vd.initializer;
                    if (initializer == null) continue;

                    assignments.add(new Java.ExpressionStatement(new Java.Assignment(
                            Location.NOWHERE,            // location
                            new Java.FieldAccessExpression(   // lhs
                                    Location.NOWHERE,                    // location
                                    new Java.ThisReference(Location.NOWHERE), // lhs
                                    vd.name                              // field
                            ),
                            "=",                         // operator
                            this.copyRvalue(initializer) // rhs
                    )));
                }

                if (assignments.isEmpty()) return new Java.EmptyStatement(Location.NOWHERE);

                if (assignments.size() == 1) return assignments.get(0);

                Java.Block result = new Java.Block(Location.NOWHERE);
                result.addStatements(assignments);
                return result;
            }

            /**
             * Add the synthetic field declarations to the class.
             */
            @Override
            public Java.TypeDeclaration
            copyPackageMemberClassDeclaration(Java.PackageMemberClassDeclaration pmcd) throws CompileException {

                assert this.moreFieldDeclarations.isEmpty();
                try {
                    Java.AbstractClassDeclaration
                            result = (Java.AbstractClassDeclaration) super.copyPackageMemberClassDeclaration(pmcd);

                    for (Java.FieldDeclaration fd : this.moreFieldDeclarations) {
                        result.addFieldDeclaration(fd); // TODO: Check for name clashes
                    }
                    return result;
                } finally {
                    this.moreFieldDeclarations.clear();
                }
            }

        }.copyAbstractCompilationUnit(cu);

        StringWriter sw = new StringWriter();
        Unparser.unparse(cu, sw);
        System.out.println(sw.toString());
    }
}