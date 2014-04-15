/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.ch;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class PrepareEncoderTest
{

    @Test
    public void testOverwrite()
    {
        long forward = PrepareEncoder.getScFwdDir();
        long backward = PrepareEncoder.getScFwdDir() ^ PrepareEncoder.getScDirMask();
        long both = PrepareEncoder.getScDirMask();
        assertTrue(PrepareEncoder.canBeOverwritten(forward, forward));
        assertTrue(PrepareEncoder.canBeOverwritten(backward, backward));
        assertTrue(PrepareEncoder.canBeOverwritten(forward, both));
        assertTrue(PrepareEncoder.canBeOverwritten(backward, both));

        assertTrue(PrepareEncoder.canBeOverwritten(both, both));
        assertFalse(PrepareEncoder.canBeOverwritten(both, forward));
        assertFalse(PrepareEncoder.canBeOverwritten(both, backward));
        assertFalse(PrepareEncoder.canBeOverwritten(forward, backward));
        assertFalse(PrepareEncoder.canBeOverwritten(backward, forward));
    }
}
