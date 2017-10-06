package com.graphhopper.api;

import com.graphhopper.util.Helper;
import okhttp3.MediaType;

/**
 *
 * @author Peter Karich
 */
public class GraphHopperMatrixWeb {

    public static final String SERVICE_URL = "service_url";
    public static final String KEY = "key";
    public static final MediaType MT_JSON = MediaType.parse("application/json; charset=utf-8");
    private final GHMatrixAbstractRequester requester;
    private String key;

    public GraphHopperMatrixWeb() {
        this(new GHMatrixBatchRequester());
    }

    public GraphHopperMatrixWeb(String serviceUrl) {
        this(new GHMatrixBatchRequester(serviceUrl));
    }

    public GraphHopperMatrixWeb(GHMatrixAbstractRequester requester) {
        this.requester = requester;
    }

    public GraphHopperMatrixWeb setKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException("Key cannot be empty");
        }

        this.key = key;
        return this;
    }

    public MatrixResponse route(GHMRequest request) {
        if (!Helper.isEmpty(key)) {
            request.getHints().put(KEY, key);
        }

        return requester.route(request);
    }
}
