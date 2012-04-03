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
package de.jetsli.graph.storage;

import de.jetsli.graph.util.BitUtil;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class FatBufferTest {

    @Test public void testGetAndPut() throws IOException {
        FatBuffer buffer = new FatBuffer("target/FatBuffer.test", 1000 * 1000, 4);
        try {
            byte[] b = BitUtil.fromInt(Integer.MAX_VALUE / 3);
            buffer.put(10, b);
            buffer.put(11, BitUtil.fromInt(Integer.MAX_VALUE / 4));
            buffer.put(12, BitUtil.fromInt(Integer.MAX_VALUE / 5));

            assertArrayEquals(b, buffer.get(10));
            assertEquals(Integer.MAX_VALUE / 3, BitUtil.toInt(buffer.get(10)));
            assertEquals(Integer.MAX_VALUE / 4, BitUtil.toInt(buffer.get(11)));
            assertEquals(Integer.MAX_VALUE / 5, BitUtil.toInt(buffer.get(12)));
        } finally {
            buffer.close();
        }
    }
}
