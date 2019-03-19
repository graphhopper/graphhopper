package com.graphhopper;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class RepeatRule implements TestRule {
    @Override
    public Statement apply(Statement base, Description description) {
        Repeat repeat = description.getAnnotation(Repeat.class);
        return repeat != null ?
                new RepeatStatement(repeat.times(), base) :
                base;
    }

    private static class RepeatStatement extends Statement {
        private final int times;
        private final Statement base;

        RepeatStatement(int times, Statement base) {
            this.times = times;
            this.base = base;
        }

        @Override
        public void evaluate() throws Throwable {
            for (int i = 0; i < times; ++i) {
                base.evaluate();
            }
        }
    }
}
