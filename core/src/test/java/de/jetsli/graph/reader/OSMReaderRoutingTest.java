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
import java.io.File;
import org.junit.After;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class OSMReaderRoutingTest extends AbstractOSMReaderTester {

    private String dir = "/tmp/OSMReaderTrialsTest";
    private OSMReader reader;

    public static void main(String[] args) {
        new OSMReaderRoutingTest().testMain();
    }

    @Override
    protected OSMReader createOSM() {
        Helper.deleteDir(new File(dir));
        new File(dir).mkdirs();

        OSMReaderRouting osm = new OSMReaderRouting(dir + "/test-db", 1000);
        osm.init(true);
        return reader = osm;
    }

    @After
    public void tearDown() {
        reader.close();
        Helper.deleteDir(new File(dir));
    }
}
