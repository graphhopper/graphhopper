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

[Graphhopper](https://graphhopper.com/) [map matching](https://github.com/graphhopper/map-matching)
is now using the hmm-lib for matching GPS positions to OpenStreetMap maps. 

The [offline-map-matching](https://github.com/bmwcarit/offline-map-matching) project
demonstrates how to use the hmm-lib for map matching but does not provide integration to any
particular map.

Besides map matching, the hmm-lib can also be used for other applications.


# Dependencies

Except for testing, there are no dependencies to other libraries.

# Contribute
Contributions are welcome! For bug reports, please create an issue. 
For code contributions (e.g. new features or bugfixes), please create a pull request.

# License

This library is licensed under the
[Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0.html).

# Changes
* 0.2.0: Extend HmmProbabilities interface to include the observation
* 0.1.0: Initial release
