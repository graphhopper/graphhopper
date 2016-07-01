# Overview

This library implements a Hidden Markov Model (HMM) for time-inhomogeneous Markov processes, meaning
that the set of states and transition probabilities may be different at each time step.
Thus, the user needs to implement an interface that computes the transition and emission/observation
probabilities as needed.

So far, this library computes the most likely sequence of states using the Viterbi algorithm.

Except for testing, there are no dependencies to other libraries.

# License

This library is licensed under the
[Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0.html).