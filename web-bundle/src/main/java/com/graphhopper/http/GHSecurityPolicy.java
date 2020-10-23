package com.graphhopper.http;

import java.lang.reflect.ReflectPermission;
import java.net.SocketPermission;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.util.PropertyPermission;

public class GHSecurityPolicy extends Policy {

    @Override
    public PermissionCollection getPermissions(CodeSource codesource) {
        PermissionCollection p = new Permissions();

        // TODO NOW limit to certain code base
        //  see e.g. https://github.com/elastic/elasticsearch/blob/master/server/src/main/resources/org/elasticsearch/bootstrap/security.policy

        // org.eclipse.jetty.io.ManagedSelector
        p.add(new RuntimePermission("setContextClassLoader"));

        // dropwizard.lifecycle.ExecutorServiceManager.stop
        p.add(new RuntimePermission("modifyThread"));

        // required for jersey
        p.add(new ReflectPermission("suppressAccessChecks"));

        // internet
        p.add(new SocketPermission("*", "resolve,accept"));
        // permissions required for maps UI
        // required to avoid NoClassDefFoundError: Could not initialize class org.eclipse.jetty.server.HttpChannelState
        p.add(new PropertyPermission("org.eclipse.jetty.server.HttpChannelState.DEFAULT_TIMEOUT", "read"));
        p.add(new PropertyPermission("org.eclipse.jetty.util.HostPort.STRIP_IPV6", "read"));
        p.add(new PropertyPermission("guava.concurrent.generate_cancellation_cause", "read"));

        // why do we need that for an API request?
        p.add(new RuntimePermission("accessDeclaredMembers"));
        // TODO NOW?
        // p.setReadOnly();
        return p;
    }
}
