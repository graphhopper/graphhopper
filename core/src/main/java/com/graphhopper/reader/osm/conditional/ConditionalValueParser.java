package com.graphhopper.reader.osm.conditional;

import ch.poole.conditionalrestrictionparser.Condition;

import java.text.ParseException;

/**
 * This interface defines how to parse a OSM value from conditional restrictions.
 */
public interface ConditionalValueParser {

    /**
     * This method checks if the condition is satisfied for this parser.
     */
    ConditionState checkCondition(String conditionalValue) throws ParseException;

    ConditionState checkCondition(Condition conditionalValue) throws ParseException;

    enum ConditionState {
        TRUE(true, true, true),
        FALSE(true, true, false),
        INVALID(false, false, false),
        UNEVALUATED(true, false, false); // ORS-GH MOD - additional value

        boolean valid;
        boolean evaluated;   // ORS-GH MOD - additional field
        boolean checkPassed;

        Condition condition; // ORS-GH MOD - additional field

	// ORS-GH MOD - additional parameter
        ConditionState(boolean valid, boolean evaluated, boolean checkPassed) {
            this.valid = valid;
            this.evaluated = evaluated;
            this.checkPassed = checkPassed;
        }

        public boolean isValid() {
            return valid;
        }

        // ORS-GH MOD START - additional method
        public boolean isEvaluated() {
            return evaluated;
        }
        // ORS-GH MOD END

        public boolean isCheckPassed() {
            if (!isValid())
                throw new IllegalStateException("Cannot call this method for invalid state");
            // ORS-GH MOD START - additional code
            if (!isEvaluated())
                throw new IllegalStateException("Cannot call this method for unevaluated state");
            // ORS-GH MOD END
            return checkPassed;
        }

        // ORS-GH MOD START - additional method
        public ConditionState setCondition(Condition condition) {
            this.condition = condition;
            return this;
        }
        // ORS-GH MOD END

        // ORS-GH MOD START - additional method
        public Condition getCondition() {
            return condition;
        }
        // ORS-GH MOD END
    }
}
