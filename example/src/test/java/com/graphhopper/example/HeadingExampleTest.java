package com.graphhopper.example;

import com.graphhopper.util.Helper;
import org.junit.jupiter.api.Test;

import java.io.File;

public class HeadingExampleTest {

    @Test
    public void main() {
        Helper.removeDir(new File("target/heading-graph-cache"));
        HeadingExample.main(new String[]{"../"});
    }
}
