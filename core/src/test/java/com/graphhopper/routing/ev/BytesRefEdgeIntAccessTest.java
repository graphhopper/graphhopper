package com.graphhopper.routing.ev;

import com.graphhopper.storage.BytesRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BytesRefEdgeIntAccessTest {

    @Test
    public void error() {
        EdgeIntAccess bytesAccess1 = new BytesRefEdgeIntAccess(new BytesRef(1));
        assertThrows(IllegalArgumentException.class, () -> bytesAccess1.setInt(0, 0, -1));

        EdgeIntAccess bytesAccess2 = new BytesRefEdgeIntAccess(new BytesRef(1));
        assertThrows(IllegalArgumentException.class, () -> bytesAccess2.setInt(0, 0, 256));
    }

    @Test
    public void testSetGet() {
        EdgeIntAccess bytesAccess = new BytesRefEdgeIntAccess(new BytesRef(2));
        bytesAccess.setInt(0, 0, 260);
        assertEquals(260, bytesAccess.getInt(0, 0));

        bytesAccess = new BytesRefEdgeIntAccess(new BytesRef(2));
        bytesAccess.setInt(0, 0, 130);
        assertEquals(130, bytesAccess.getInt(0, 0));

        bytesAccess = new BytesRefEdgeIntAccess(new BytesRef(2));
        bytesAccess.setInt(0, 0, 260);
        assertEquals(260, bytesAccess.getInt(0, 0));
    }
}
