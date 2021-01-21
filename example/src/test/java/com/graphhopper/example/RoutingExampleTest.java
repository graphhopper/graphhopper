package com.graphhopper.example;

import com.graphhopper.util.Helper;
import org.junit.jupiter.api.Test;

import java.io.File;

public class RoutingExampleTest {

    @Test
    public void main() {
        Helper.removeDir(new File("target/routing-graph-cache"));
        RoutingExample.main(new String[]{"../"});

        Helper.removeDir(new File("target/routing-tc-graph-cache"));
        RoutingExampleTC.main(new String[]{"../"});
    }
}
