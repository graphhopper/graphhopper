# Overview

This library implements Hidden Markov Models (HMM) for time-inhomogeneous Markov processes.
This means that, in contrast to many other HMM implementations, there can be different
states and a different transition matrix at each time step.

Currently, this library provides an implementation of the Viterbi algorithm, which computes the
most likely sequence of states. More HMM algorithms such as the forward backward algorithm will
follow.

# Applications

This library was initially created for HMM-based map matching according to the paper
"NEWSON, Paul; KRUMM, John. Hidden Markov map matching through noise and sparseness.
In: Proceedings of the 17th ACM SIGSPATIAL international conference on advances in geographic
information systems. ACM, 2009. S. 336-343."

[Graphhopper](https://graphhopper.com/) [map matching](https://github.com/graphhopper/graphhopper/tree/master/map-matching)
is using the hmm-lib for matching GPS positions to the OpenStreetMap road network.

The [offline-map-matching](https://github.com/bmwcarit/offline-map-matching) project
demonstrates how to use the hmm-lib for map matching but does not provide integration to any
particular map.

Besides map matching, the hmm-lib can also be used for other applications.

# License

This library is licensed under the
[Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0.html).

# Dependencies

Except for testing, there are no dependencies to other libraries.

# Maven

To use this library, add the following to your pom.xml:

```
  <dependencies>
    ...
    <dependency>
      <groupId>com.bmw.hmm</groupId>
      <artifactId>hmm-lib</artifactId>
      <version>1.0.0</version>
    </dependency>
  </dependencies>

  <repositories>
    ...
    <repository>
      <id>hmm-lib-releases</id>
      <url>https://raw.github.com/bmwcarit/hmm-lib/mvn-releases/</url>
    </repository>
  </repositories>
```


If you want to use snapshots, add
```
  <repositories>
    ...
    <repository>
      <id>hmm-lib-snapshots</id>
      <url>https://raw.github.com/bmwcarit/hmm-lib/mvn-snapshots/</url>
      <snapshots>
        <enabled>true</enabled>
        <updatePolicy>always</updatePolicy>
      </snapshots>
    </repository>
  </repositories>
```

# Contribute
Contributions are welcome! For bug reports, please create an issue. 
For code contributions (e.g. new features or bugfixes), please create a pull request.

# Changes
* 1.0.0:
  * API redesign to allow calling the Viterbi algorithm iteratively. This gives the library user
   increased flexibility and optimization opportunities when computing transition and observation
   probabilities. Moreover, the new API enables better handling of HMM breaks.
  * Add support for transition descriptors. For map matching, this allows retrieving the paths
   between matched positions (the entire matched route) after computing the most likely sequence.
  *  Reduce memory footprint from O(t\*n�) to O(t\*n) or even O(t) in many applications, where t is
    the number of  time steps and n is the number of candidates per time step. 
* 0.2.0: Extend HmmProbabilities interface to include the observation
* 0.1.0: Initial release
