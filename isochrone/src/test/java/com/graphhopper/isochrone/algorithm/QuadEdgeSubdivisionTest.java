package com.graphhopper.isochrone.algorithm;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.triangulate.IncrementalDelaunayTriangulator;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QuadEdgeSubdivisionTest {

    @Test
    public void testJtsDelaunayTriangulator() {
        Vertex v1 = new Vertex(0.0, 0.0, 0.0);
        Vertex v2 = new Vertex(1.0, -1.0, 1.0);
        Vertex v3 = new Vertex(1.0, 1.0, 0.0);
        Vertex v4 = new Vertex(2.0, 0.0, 0.0);
        Vertex v5 = new Vertex(1.0, -3.0, 0.0);
        QuadEdgeSubdivision quadEdgeSubdivision = new QuadEdgeSubdivision(new Envelope(0.0, 2.0, -1.0, 1.0), 0.001);
        IncrementalDelaunayTriangulator triangulator = new IncrementalDelaunayTriangulator(quadEdgeSubdivision);
        triangulator.insertSite(v1);
        triangulator.insertSite(v2);
        triangulator.insertSite(v3);
        triangulator.insertSite(v4);
        triangulator.insertSite(v5);
        assertEquals(5, quadEdgeSubdivision.getVertices(false).size());
    }

    @Test
    public void createQuadEdgeSubdivisionFromScratch() {
        Vertex v1 = new Vertex(0.0, 0.0, 0.0);
        Vertex v2 = new Vertex(1.0, -1.0, 1.0);
        Vertex v3 = new Vertex(1.0, 1.0, 0.0);
        Vertex v4 = new Vertex(2.0, 0.0, 0.0);
        QuadEdgeSubdivision quadEdgeSubdivision = new QuadEdgeSubdivision(new Envelope(0.0, 2.0, -1.0, 1.0), 0.001);

        QuadEdge e1 = quadEdgeSubdivision.makeEdge(v1, v3);
        QuadEdge e2 = quadEdgeSubdivision.makeEdge(v3, v2);
        QuadEdge.splice(e1.sym(), e2);
        QuadEdge e3 = quadEdgeSubdivision.connect(e2, e1);



        QuadEdge e4 = quadEdgeSubdivision.makeEdge(v4, v2);
        QuadEdge.splice(e2.sym(), e4.lNext());
        QuadEdge e41 = quadEdgeSubdivision.connect(e2.sym(), e4);

        Vertex v5 = new Vertex(1.0, -3.0, 0.0);
        QuadEdge e5 = quadEdgeSubdivision.makeEdge(v5, v1);
        QuadEdge.splice(e3.sym(), e5.lNext());
        QuadEdge e6 = quadEdgeSubdivision.connect(e3.sym(), e5);
        QuadEdge e7 = quadEdgeSubdivision.connect(e4.sym(), e6.sym());

        assertTriangle(e1, e2, e3);
        assertTriangle(e4, e2.sym(), e41);
        assertTriangle(e5, e3.sym(), e6);
        assertTriangle(e6.sym(), e4.sym(), e7);

        assertVertex(e1, e3.sym(), e5.sym());
        assertVertex(e5, e6.sym(), e7.sym());
        assertVertex(e7, e4, e41.sym());
        assertVertex(e41, e2, e1.sym());
        assertVertex(e3, e2.sym(), e4.sym(), e6);

        ContourBuilder contourBuilder = new ContourBuilder(quadEdgeSubdivision.getEdges());

        Geometry geometry = contourBuilder.computeIsoline(0.5);
        assertEquals("MULTIPOLYGON (((1 0, 0.5 -0.5, 1 -2, 1.5 -0.5, 1 0, 1 0)))", geometry.toString());
    }

    @Test
    public void createQuadEdgeSubdivisionFromTriangleList() {
        Triangulation triangulation = new Triangulation();

        triangulation.getVertices().put(1, new Vertex(0.0, 0.0, 0.0));
        triangulation.getVertices().put(2, new Vertex(1.0, -1.0, 1.0));
        triangulation.getVertices().put(3, new Vertex(1.0, 1.0, 0.0));
        triangulation.getVertices().put(4, new Vertex(2.0, 0.0, 0.0));
        triangulation.getVertices().put(5, new Vertex(1.0, -3.0, 0.0));

        triangulation.makeTriangle(1, 3, 2);
        triangulation.makeTriangle(1, 2, 5);
        triangulation.makeTriangle(2, 4, 5);
        triangulation.makeTriangle(3, 4, 2);

        QuadEdge e1 = triangulation.getEdge(1,3);
        QuadEdge e2 = triangulation.getEdge(3,2);
        QuadEdge e3 = triangulation.getEdge(2,1);
        QuadEdge e4 = triangulation.getEdge(4,2);
        QuadEdge e41 = triangulation.getEdge(3,4);
        QuadEdge e5 = triangulation.getEdge(5,1);
        QuadEdge e6 = triangulation.getEdge(2,5);
        QuadEdge e7 = triangulation.getEdge(4,5);

        assertTriangle(e1, e2, e3);
        assertTriangle(e4, e2.sym(), e41);
        assertTriangle(e5, e3.sym(), e6);
        assertTriangle(e6.sym(), e4.sym(), e7);

        assertVertex(e1, e3.sym(), e5.sym());
        assertVertex(e5, e6.sym(), e7.sym());
        assertVertex(e7, e4, e41.sym());
        assertVertex(e41, e2, e1.sym());
        assertVertex(e3, e2.sym(), e4.sym(), e6);

        ContourBuilder contourBuilder = new ContourBuilder(triangulation.getEdges());

        Geometry geometry = contourBuilder.computeIsoline(0.5);
        assertEquals("MULTIPOLYGON (((0.5 -0.5, 1 -2, 1.5 -0.5, 1 0, 0.5 -0.5)))", geometry.toString());
    }

    @Test
    public void createQuadEdgeSubdivisionFromTriangleList2() {
        Triangulation triangulation = new Triangulation();

        triangulation.getVertices().put(0, new Vertex(0.0, -1.0, 0.0));
        triangulation.getVertices().put(1, new Vertex(0.0, 0.0, 0.0));
        triangulation.getVertices().put(2, new Vertex(1.0, -1.0, 1.0));
        triangulation.getVertices().put(3, new Vertex(1.0, 1.0, 0.0));
        triangulation.getVertices().put(4, new Vertex(2.0, 0.0, 0.0));
        triangulation.getVertices().put(5, new Vertex(1.0, -3.0, 0.0));

        triangulation.makeTriangle(0, 3, 1);
        triangulation.makeTriangle(1, 2, 5);
        triangulation.makeTriangle(2, 4, 5);
        triangulation.makeTriangle(3, 4, 2);
        triangulation.makeTriangle(1, 3, 2);

        QuadEdge e1 = triangulation.getEdge(1,3);
        QuadEdge e2 = triangulation.getEdge(3,2);
        QuadEdge e3 = triangulation.getEdge(2,1);
        QuadEdge e4 = triangulation.getEdge(4,2);
        QuadEdge e41 = triangulation.getEdge(3,4);
        QuadEdge e5 = triangulation.getEdge(5,1);
        QuadEdge e6 = triangulation.getEdge(2,5);
        QuadEdge e7 = triangulation.getEdge(4,5);
        QuadEdge e0 = triangulation.getEdge(1, 0);
        QuadEdge e00 = triangulation.getEdge(3, 0);

        assertTriangle(e1, e2, e3);
        assertTriangle(e4, e2.sym(), e41);
        assertTriangle(e5, e3.sym(), e6);
        assertTriangle(e6.sym(), e4.sym(), e7);

        assertVertex(e1, e3.sym(), e5.sym(), e0);
        assertVertex(e5, e6.sym(), e7.sym());
        assertVertex(e7, e4, e41.sym());
        assertVertex(e41, e2, e1.sym(), e00);
        assertVertex(e3, e2.sym(), e4.sym(), e6);

        ContourBuilder contourBuilder = new ContourBuilder(triangulation.getEdges());

        Geometry geometry = contourBuilder.computeIsoline(0.5);
        assertEquals("MULTIPOLYGON (((0.5 -0.5, 1 -2, 1.5 -0.5, 1 0, 0.5 -0.5)))", geometry.toString());
    }

    private void assertVertex(QuadEdge ee1, QuadEdge ee2, QuadEdge ee3) {
        assertEquals(ee2, ee1.oNext());
        assertEquals(ee3, ee2.oNext());
        assertEquals(ee1, ee3.oNext());
    }

    private void assertVertex(QuadEdge ee1, QuadEdge ee2, QuadEdge ee3, QuadEdge ee4) {
        assertEquals(ee2, ee1.oNext());
        assertEquals(ee3, ee2.oNext());
        assertEquals(ee4, ee3.oNext());
        assertEquals(ee1, ee4.oNext());
    }

    private void assertTriangle(QuadEdge e1, QuadEdge e2, QuadEdge e3) {
        assertEquals(e2, e1.lNext());
        assertEquals(e3, e2.lNext());
        assertEquals(e1, e3.lNext());
    }

}
