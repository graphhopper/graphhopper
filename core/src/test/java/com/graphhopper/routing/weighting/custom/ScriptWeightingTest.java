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
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "edge.getDistance()*7/*test", allNamesValid));
        assertFalse(ScriptWeighting.parseAndGuessParameters(set, "edge.getDistance()//*test", allNamesValid));
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

        // safely inject the following expression into getValue and add convenience helper variable 'road_class' before
        String userExpression = "road_class == PRIMARY ? 0.7 : 0.8";
        String classTemplate = ""
                + "import " + PriorityScript.class.getName() + ";"
                + "import " + EncodedValueLookup.class.getName() + ";"
                + "import " + EnumEncodedValue.class.getName() + ";"
                + "import " + EdgeIteratorState.class.getName() + ";"
                + "import " + RoadClass.class.getName() + ";"
                + "import static " + RoadClass.class.getName() + ".*;"
                + "public class Test extends PriorityScript {"
                + "   EnumEncodedValue road_class_enc2;"
                + "   @Override "
                + "   public void init(EncodedValueLookup lookup) {"
                + "      road_class_enc2 = lookup.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);"
                + "   }"
                + "   @Override "
                + "   public double getValue(EdgeIteratorState edge, boolean reverse) {"
                + "      return 0.17; //will be overwritten by code injected in DeepCopier\n"
                + "   }"
                + "}";
        Java.AbstractCompilationUnit cu = new Parser(new Scanner("ignore", new StringReader(classTemplate))).
                parseAbstractCompilationUnit();

        // instead of string appending safely add the expression via Java:
        cu = new DeepCopier() {

            @Override
            public Java.MethodDeclarator copyMethodDeclarator(Java.MethodDeclarator subject) throws CompileException {
                if (!subject.name.equals("getValue"))
                    return super.copyMethodDeclarator(subject);

                try {
                    Parser parser = new Parser(new Scanner("parser1", new StringReader("Enum road_class = edge.get(road_class_enc2);")));
                    List<Java.BlockStatement> statements = new ArrayList<>(parser.parseBlockStatements());

                    parser = new Parser(new Scanner("parser2", new StringReader(userExpression)));
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
        prio.init(encodingManager);
        assertEquals(0.7, prio.getValue(primary, false));
    }

    @Test
    public void testCBE() throws Exception {
        EdgeIteratorState primary = graphHopperStorage.edge(0, 1, 10, true).
                set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80);

        ClassBodyEvaluator evaluator = new ClassBodyEvaluator();
        evaluator.setExtendedType(PriorityScript.class);
        evaluator.setClassName("Test");
        evaluator.setDefaultImports("com.graphhopper.routing.weighting.custom.*", "com.graphhopper.routing.ev.*",
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
        assertEquals(PRIMARY.ordinal(), entry.getValue(primary, false));
    }
}