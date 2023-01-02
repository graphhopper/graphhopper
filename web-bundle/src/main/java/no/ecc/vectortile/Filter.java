package no.ecc.vectortile;

import java.util.Set;

/**
 * A filter which can be passed to a VectorTile decoder to optimize performance by only decoding layers of interest.
 */
public abstract class Filter {

    public abstract boolean include(String layerName);

    public static final Filter ALL = new Filter() {

        @Override
        public boolean include(String layerName) {
            return true;
        }

    };

    /**
     * A filter that only lets a single named layer be decoded.
     */
     public static final class Single extends Filter {

        private final String layerName;

        public Single(String layerName) {
            this.layerName = layerName;
        }

        @Override
        public boolean include(String layerName) {
            return this.layerName.equals(layerName);
        }

    }

    /**
     * A filter that only allows the named layers to be decoded.
     */
    public static final class Any extends Filter {

        private final Set<String> layerNames;

        public Any(Set<String> layerNames) {
            this.layerNames = layerNames;
        }

        @Override
        public boolean include(String layerName) {
            return this.layerNames.contains(layerName);
        }

    }

}
