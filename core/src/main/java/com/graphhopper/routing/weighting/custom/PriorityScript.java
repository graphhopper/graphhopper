package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import org.codehaus.janino.ExpressionEvaluator;

import java.security.*;
import java.util.Map;

public abstract class PriorityScript implements EdgeToValueEntry {

    // private is not possible as we create a dynamic subclass that needs access to it
    protected EnumEncodedValue<RoadClass> road_class;

    public PriorityScript() {
    }

    public static EdgeToValueEntry create(CustomModel customModel, EncodedValueLookup lookup) {
        ExpressionEvaluator ee = new ExpressionEvaluator();

        ee.setClassName("Priority");
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
        ee.setExtendedClass(PriorityScript.class);
        ee.setMethodNames(new String[]{
                "getValue",
        });
        ee.setParameters(new String[][]{
                {"edge", "reverse"},
        }, new Class[][]{
                {EdgeIteratorState.class, boolean.class},
        });

        String expression = "";
        boolean closedScript = false;
        for (Map.Entry<String, Object> entry : customModel.getPriority().entrySet()) {
            if (entry.getKey().contains("new ") || entry.getValue().toString().contains("new "))
                throw new IllegalArgumentException("Object allocation is not allowed in expression: " + entry.toString());

            if (!expression.isEmpty())
                expression += " : ";

            if (entry.getKey().equals(CustomWeighting.CATCH_ALL)) {
                expression += entry.getValue();
                closedScript = true;
                break;
            } else {
                expression += entry.getKey().replaceAll("e\\(", "edge.get(") + " ? " + entry.getValue();
            }
        }

        if (!closedScript)
            expression += ": 1";

        try {
            ee.cook(new String[]{expression});
            PriorityScript priorityScript = (PriorityScript) ee.getClazz().getDeclaredConstructor().newInstance();
            priorityScript.road_class = lookup.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
            return priorityScript;
        } catch (Exception ex) {
            throw new IllegalArgumentException("In " + expression, ex);
        }
    }

    void protectedRun(EdgeIterator iter, boolean reverse) {
        try {
            AccessController.doPrivileged(
                    (PrivilegedExceptionAction<Object>) () -> {
                        PriorityScript.this.getValue(iter, reverse);
                        return null;
                    },
                    new AccessControlContext(new ProtectionDomain[]{
                            new ProtectionDomain(null, new Permissions())
                    }));
        } catch (PrivilegedActionException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
