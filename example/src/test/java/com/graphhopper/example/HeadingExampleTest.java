package com.graphhopper.example;

import java.io.File;

import org.junit.jupiter.api.Test;

import com.graphhopper.util.Helper;

public class HeadingExampleTest {
  
  @Test
  public void main() {
      Helper.removeDir(new File("target/heading-graph-cache"));
      HeadingExample.main(new String[]{"../"});
  }
}
