/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.reader;

import de.jetsli.graph.util.Helper;
import de.jetsli.graph.util.StopWatch;
import java.util.Date;
import java.util.Random;

/**
 * @author Peter Karich
 */
public class MiniPerfTest {

    protected int max = 20;
    protected String name = "";
    protected boolean showProgress = true;
    protected Random random = new Random(0);

    public MiniPerfTest(String name) {
        this.name = name;
    }

    public MiniPerfTest setShowProgress(boolean showProgress) {
        this.showProgress = showProgress;
        return this;
    }

    public MiniPerfTest setSeed(long seed) {
        random.setSeed(seed);
        return this;
    }

    /**
     * set the iterations
     */
    public MiniPerfTest setMax(int max) {
        this.max = max;
        return this;
    }

    public void start() {
        int maxNo = max / 4;
        long res = 0;
        System.out.println(new Date() + "# start performance **" + name + "**, iterations:" + max);
        StopWatch sw = new StopWatch().start();
        for (int i = 0; i < maxNo; i++) {
            res += doJvmInit(i);
        }
        if (showProgress)
            System.out.println(new Date() + "# jvm initialized! secs/iter:" + sw.stop().getSeconds() / maxNo + ", res:" + res);
        res = 0;
        sw = new StopWatch().start();
        maxNo = max;
        float partition = 5.0F;
        int part = (int) (maxNo / partition);
        for (int i = 0; i < maxNo; i++) {
            if (showProgress && i % part == 0)
                System.out.println(new Date() + "# progress " + i * 100L / maxNo
                        + "% => secs/iter:" + (sw.stop().start().getSeconds() / i));
            res += doCalc(i);
        }
        System.out.println(new Date() + "# progress 100% in " + sw.stop().getSeconds()
                + " secs => secs/iter:" + sw.stop().getSeconds() / maxNo + "\n avoid jvm removal:"
                + res + ", memInfo:" + Helper.getMemInfo() + " " + Helper.getBeanMemInfo() + "\n");
    }

    /**
     * @return something meaningless to avoid that jvm optimizes away the inner code
     */
    public long doJvmInit(int run) {
        return doCalc(-run);
    }

    public long doCalc(int run) {
        return run;
    }
}
