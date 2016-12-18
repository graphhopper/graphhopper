package com.graphhopper.reader.osm.conditional;

import java.text.ParseException;

/**
 * This interface defines how to parse a OSM value from conditional restrictions.
 */
public interface ConditionalValueParser {

    /**
     * This method checks if the condition is satisfied for this parser.
     */
    ConditionState checkCondition(String conditionalValue) throws ParseException;

    enum ConditionState {
        TRUE(true, true),
        FALSE(true, false),
        INVALID(false, false);

        boolean valid;
        boolean checkPassed;

        ConditionState(boolean valid, boolean checkPassed) {
            this.valid = valid;
            this.checkPassed = checkPassed;
        }

        public boolean isValid() {
            return valid;
        }

        public boolean isCheckPassed() {
            if (!isValid())
                throw new IllegalStateException("Cannot call this method for invalid state");

            return checkPassed;
        }
    }
}
