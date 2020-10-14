package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.util.EdgeIteratorState;
import org.codehaus.janino.ScriptEvaluator;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

import static com.graphhopper.routing.weighting.custom.ScriptWeighting.parseAndGuessParameters;

public abstract class PriorityScript implements EdgeToValueEntry {

    // private is not possible as we create a dynamic subclass that needs access to it
    protected EnumEncodedValue road_class_enc;
    protected EnumEncodedValue road_environment_enc;
    protected EnumEncodedValue surface_enc;
    protected EnumEncodedValue toll_enc;

    public PriorityScript() {
    }

    public void init(EncodedValueLookup lookup) {
        road_class_enc = lookup.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        road_environment_enc = lookup.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        if (lookup.hasEncodedValue(Surface.KEY))
            surface_enc = lookup.getEnumEncodedValue(Surface.KEY, Surface.class);
        if (lookup.hasEncodedValue(Toll.KEY))
            toll_enc = lookup.getEnumEncodedValue(Toll.KEY, Toll.class);
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

        String mainExpression = "";
        boolean closedScript = false;
        HashSet<String> createObjects = new HashSet<>();
        ScriptWeighting.NameValidator nameValidator = name ->
                // allow all encoded values and constants
                lookup.hasEncodedValue(name) || name.toUpperCase(Locale.ROOT).equals(name);
        for (Map.Entry<String, Object> entry : customModel.getPriority().entrySet()) {
            if (!mainExpression.isEmpty())
                mainExpression += " : ";

            // TODO NOW value must be a number or a name -> no method and no boolean expression
            if (entry.getKey().equals(CustomWeighting.CATCH_ALL)) {
                if (!parseAndGuessParameters(createObjects, entry.getValue().toString(), nameValidator))
                    throw new IllegalArgumentException("Value not a valid, simple expression: " + entry.getValue().toString());
                mainExpression += entry.getValue();
                closedScript = true;
                break;
            } else {
                if (!parseAndGuessParameters(createObjects, entry.getKey(), nameValidator))
                    throw new IllegalArgumentException("Key not a valid, simple expression: " + entry.getKey());
                if (!parseAndGuessParameters(createObjects, entry.getValue().toString(), nameValidator))
                    throw new IllegalArgumentException("Value not a valid, simple expression: " + entry.getValue().toString());

                // TODO should we build the expressions via Java? new Java.ConditionalExpression(location, lhs, mhs, rhs);
                mainExpression += entry.getKey() + " ? " + entry.getValue();
            }
        }

        if (!closedScript)
            mainExpression += ": 1";

        String getValueCode = "";
        for (String arg : createObjects) {
            if (lookup.hasEncodedValue(arg))
                getValueCode += "Enum " + arg + " = reverse ? edge.getReverse(" + arg + "_enc) : edge.get(" + arg + "_enc);\n";
        }
        getValueCode += "return " + mainExpression + ";";

        try {
            se.cook(new String[]{getValueCode});
            // TODO see if the following is possible and faster:
            // se.createFastEvaluator
            PriorityScript priorityScript = (PriorityScript) se.getClazz().getDeclaredConstructor().newInstance();
            priorityScript.init(lookup);
            return priorityScript;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Problem with: " + mainExpression, ex);
        }
    }
}
