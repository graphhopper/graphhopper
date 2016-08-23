package com.graphhopper.util.exceptions;

import java.util.Map;

/**
 *
 * Extends IllegalArgumentException because we explicitly catch that in the GraphHopperServlet - TODO we might use something else?
 *
 * @author Robin Boldt
 */
public abstract class GHIllegalArgumentException extends IllegalArgumentException
{

    public GHIllegalArgumentException() {
    }

    public GHIllegalArgumentException(String var1) {
        super(var1);
    }

    public GHIllegalArgumentException(String var1, Throwable var2) {
        super(var1, var2);
    }

    public GHIllegalArgumentException(Throwable var1) {
        super(var1);
    }

    public abstract Map<String, String> getIllegalArgumentDetails();

}
