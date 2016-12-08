package com.graphhopper.http;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.graphhopper.util.CmdArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;

public class CmdArgsModule extends AbstractModule {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final CmdArgs args;

    public CmdArgsModule(CmdArgs args) {
        this.args = args;
    }

    @Provides
    @Singleton
    @Named("timeout")
    Long getTimeout(CmdArgs args) {
        return args.getLong("web.timeout", 3000);
    }

    @Provides
    @Singleton
    @Named("jsonp_allowed")
    Boolean isJsonpAllowed(CmdArgs args) {
        boolean jsonpAllowed = args.getBool("web.jsonp_allowed", false);
        if (!jsonpAllowed)
            logger.info("jsonp disabled");
        return jsonpAllowed;
    }

    @Override
    protected void configure() {
        bind(CmdArgs.class).toInstance(args);
    }
}
