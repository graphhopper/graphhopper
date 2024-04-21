package com.graphhopper.routing.ev;

import com.graphhopper.storage.BytesRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BytesRefEdgeIntAccessTest {

    @Test
    public void testSetGet() {
        EdgeIntAccess bytesAccess = new BytesRefEdgeIntAccess(new BytesRef(1));
        bytesAccess.setInt(0, 0, -1);
        assertEquals(-1, bytesAccess.getInt(0, 0));

        bytesAccess = new BytesRefEdgeIntAccess(new BytesRef(2));
        bytesAccess.setInt(0, 0, 260);
        assertEquals(260, bytesAccess.getInt(0, 0));

        bytesAccess = new BytesRefEdgeIntAccess(new BytesRef(2));
        bytesAccess.setInt(0, 0, -130);
        assertEquals(-130, bytesAccess.getInt(0, 0));

        bytesAccess = new BytesRefEdgeIntAccess(new BytesRef(2));
        bytesAccess.setInt(0, 0, -260);
        assertEquals(-260, bytesAccess.getInt(0, 0));

        // no need to always support negative numbers as we only store positive numbers (we add "minStorableValue")
        BytesRefEdgeIntAccess bytesAccessTmp = new BytesRefEdgeIntAccess(new BytesRef(3));
        assertThrows(IllegalArgumentException.class, () -> bytesAccessTmp.setInt(0, 0, -32800));
    }

}
