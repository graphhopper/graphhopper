package com.graphhopper.http;

import org.eclipse.jetty.util.component.LifeCycle;

import java.io.FilePermission;
import java.security.Permission;
import java.security.Policy;

// TODO NOW use SecureSM
//  https://github.com/elastic/securesm
//  embed directly like it is done here? https://github.com/elastic/elasticsearch/blob/master/libs/secure-sm/src/main/java/org/elasticsearch/secure_sm/SecureSM.java
// TODO NOW make this all working for testing too, see BootstrapForTesting
public class GHSecurityManager extends SecurityManager {

    boolean log = false;

    @Override
    public void checkPermission(Permission perm) {
        // TODO NOW add permissions for certain files only (and from a limited context) instead of granting them all
        //  see https://github.com/elastic/elasticsearch/blob/master/server/src/main/java/org/elasticsearch/bootstrap/Security.java
        //  and FilePermissionUtils
        if (perm instanceof FilePermission && perm.getActions().equals("read"))
            return;

//        if (log)
//            System.out.println("p.add(new " + perm.getClass().getSimpleName() + "(\"" + perm.getName() + "\", \"" + perm.getActions() + "\"));");
        super.checkPermission(perm);
    }

    /**
     * As the very first call we need to grant all permissions and later set to GHSecurityManager (see {@link DWLifeCycleListener}).
     * Otherwise many obscure and very broad permissions needs to be granted.
     */
    public static void init() {
        Policy.setPolicy(new GHSecurityPolicy());
        System.setSecurityManager(new SecurityManager() {
            @Override
            public void checkPermission(Permission perm) {
            }

            @Override
            public void checkPermission(Permission perm, Object context) {
            }
        });
    }

    /**
     * Add listener to lifecycle to set our SecurityManager after all the initialization is done. Use via:
     * <pre>
     * environment.lifecycle().addLifeCycleListener(new GHSecurityManager.DWLifeCycleListener());
     * </pre>
     */
    public static class DWLifeCycleListener implements LifeCycle.Listener {
        @Override
        public void lifeCycleStarting(LifeCycle lifeCycle) {
        }

        @Override
        public void lifeCycleStarted(LifeCycle lifeCycle) {
            System.setSecurityManager(new GHSecurityManager());
        }

        @Override
        public void lifeCycleFailure(LifeCycle lifeCycle, Throwable throwable) {
        }

        @Override
        public void lifeCycleStopping(LifeCycle lifeCycle) {
        }

        @Override
        public void lifeCycleStopped(LifeCycle lifeCycle) {
        }
    }
}
