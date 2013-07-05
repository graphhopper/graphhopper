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
package com.graphhopper.reader;

import com.graphhopper.coll.GHLongLongBTree;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author Nop
 */
public class OSMDemTest
{
    @Test
    public void testIdEleStore()
    {
        final OsmNodeTranslation3D table = new OsmNodeTranslation3D();

        try
        {
            table.setElevation(4711, 15);
            assertTrue(false);
        } catch (Exception e)
        {
            assertTrue(true);
        }
        try
        {
            table.getElevation( 4711 );
            assertTrue(false);
        } catch (Exception e)
        {
            assertTrue(true);
        }

        table.putIndex( 4711, 10 );
        assertEquals(-1, table.getIndex(4710));
        assertEquals(10, table.getIndex(4711));
        table.putIndex(4710, 20);
        table.putIndex(4000, 30);
        table.putIndex(5000, 40);

        table.setElevation( 5000, 400 );
        table.setElevation( 4000, 300 );
        table.setElevation( 4711, 100 );

        final long[] ids = new long[] { 4000, 4710, 4711, 5000 };
        final long[] idx = new long[] { 30, 20, 10, 40 };
        final long[] eles = new long[] { 300, 0, 100, 400 };

        table.vistKeys(new GHLongLongBTree.Visitor()
        {
            int i = 0;
            @Override
            public void processKey( long key )
            {
                assertEquals( ids[i], key );
                assertEquals( table.getIndex( key ), idx[i]);
                assertEquals( table.getElevation(key), eles[i]);
                i++;
            }
        });
    }
}