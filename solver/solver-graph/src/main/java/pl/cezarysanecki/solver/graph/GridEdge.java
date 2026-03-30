package pl.cezarysanecki.solver.graph;

/**
 * Edge data in a grid graph — traversal direction.
 *
 * @param direction movement direction from source to target
 */
public record GridEdge(Direction direction) {

    /**
     * Movement directions on a 2D grid.
     */
    public enum Direction {
        UP(-1, 0),
        DOWN(1, 0),
        LEFT(0, -1),
        RIGHT(0, 1),
        UP_LEFT(-1, -1),
        UP_RIGHT(-1, 1),
        DOWN_LEFT(1, -1),
        DOWN_RIGHT(1, 1);

        private final int dRow;
        private final int dCol;

        Direction(int dRow, int dCol) {
            this.dRow = dRow;
            this.dCol = dCol;
        }

        public int dRow() {
            return dRow;
        }

        public int dCol() {
            return dCol;
        }
    }
}
