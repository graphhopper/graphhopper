package com.graphhopper.http;

import ch.qos.logback.access.spi.IAccessEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.dropwizard.logging.filter.FilterFactory;

@JsonTypeName("http-errors-only")
public class HttpErrorsOnlyFilterFactory implements FilterFactory<IAccessEvent> {
    @Override
    public Filter<IAccessEvent> build() {
        return new Filter<IAccessEvent>() {
            @Override
            public FilterReply decide(IAccessEvent event) {
                return event.getStatusCode() >= 400 ? FilterReply.NEUTRAL : FilterReply.DENY;
            }
        };
    }
}
