# Build & project structure

---

## Technologies

| Aspect | Choice |
|--------|--------|
| Language | Java 24 |
| Build | Gradle 8.14 Kotlin DSL |
| Tests | JUnit 5 (Jupiter) |
| Runtime deps | **zero** |
| Base package | `pl.cezarysanecki.solver` |

---

## Directory structure

```
solver/
├── settings.gradle.kts          # multi-module settings
├── build.gradle.kts             # shared configuration (Java version, test config)
├── docs/                        # documentation (these files)
│   ├── 01-overview.md
│   ├── 02-api.md
│   ├── 03-core.md
│   ├── 04-graph.md
│   ├── 05-int-fast.md
│   ├── 06-build.md
│   └── 07-roadmap.md
│
├── solver-api/
│   ├── build.gradle.kts
│   └── src/
│       └── main/java/pl/cezarysanecki/solver/api/
│           ├── CostFunction.java
│           ├── DoubleAlgebra.java
│           ├── Edge.java
│           ├── FiniteGraph.java
│           ├── Graph.java
│           ├── Heuristic.java
│           ├── IntAlgebra.java
│           ├── LongAlgebra.java
│           ├── Path.java
│           ├── ShortestPathSolver.java
│           └── WeightAlgebra.java
│
├── solver-core/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/pl/cezarysanecki/solver/core/
│       │   ├── AStar.java
│       │   ├── BidirectionalDijkstra.java
│       │   ├── Dijkstra.java
│       │   ├── MinHeap.java
│       │   ├── SimplePath.java
│       │   └── package-info.java
│       └── test/java/pl/cezarysanecki/solver/core/
│           ├── AlgorithmComparisonTest.java
│           ├── AStarTest.java
│           ├── BidirectionalDijkstraTest.java
│           ├── DijkstraTest.java
│           └── MinHeapTest.java
│
├── solver-graph/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/pl/cezarysanecki/solver/graph/
│       │   ├── AdjacencyListGraph.java
│       │   ├── GridEdge.java
│       │   ├── GridGraph.java
│       │   ├── GridNode.java
│       │   ├── ReversedGraph.java
│       │   ├── package-info.java
│       │   └── heuristics/
│       │       ├── GridHeuristics.java
│       │       └── package-info.java
│       └── test/java/pl/cezarysanecki/solver/graph/
│           ├── AdjacencyListGraphTest.java
│           ├── GridGraphTest.java
│           ├── ReversedGraphTest.java
│           ├── SimpleGridNode.java
│           └── heuristics/
│               └── GridHeuristicsTest.java
│
└── solver-int/                  # EMPTY — future
    ├── build.gradle.kts
    └── src/
        └── main/java/pl/cezarysanecki/solver/intfast/
            └── (package-info.java)
```

---

## Gradle — settings.gradle.kts

```kotlin
rootProject.name = "solver"

include("solver-api")
include("solver-core")
include("solver-graph")
include("solver-int")
```

---

## Gradle — root build.gradle.kts

```kotlin
plugins {
    java
}

subprojects {
    apply(plugin = "java")

    group = "pl.cezarysanecki.solver"
    version = "0.1.0-SNAPSHOT"

    java {
        sourceCompatibility = JavaVersion.VERSION_24
        targetCompatibility = JavaVersion.VERSION_24
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        testImplementation(platform("org.junit:junit-bom:5.11.4"))
        testImplementation("org.junit.jupiter:junit-jupiter")
    }

    tasks.test {
        useJUnitPlatform()
    }
}
```

---

## Gradle — solver-api/build.gradle.kts

```kotlin
// solver-api: zero dependencies (neither runtime nor from other modules)
```

File empty or with minimal settings — solver-api depends on nothing.

---

## Gradle — solver-core/build.gradle.kts

```kotlin
dependencies {
    implementation(project(":solver-api"))
}
```

---

## Gradle — solver-graph/build.gradle.kts

```kotlin
dependencies {
    implementation(project(":solver-api"))

    // tests use solver-core for end-to-end tests
    testImplementation(project(":solver-core"))
}
```

---

## Gradle — solver-int/build.gradle.kts

```kotlin
dependencies {
    implementation(project(":solver-api"))
}
```

---

## Commands

```bash
# Full build + tests
./gradlew build

# Tests only
./gradlew test

# Tests for a specific module
./gradlew :solver-core:test

# Specific test class
./gradlew :solver-core:test --tests "pl.cezarysanecki.solver.core.DijkstraTest"

# Specific test method
./gradlew :solver-core:test --tests "pl.cezarysanecki.solver.core.DijkstraTest.shouldFindShortestPath"

# Clean
./gradlew clean

# Build without tests
./gradlew build -x test
```

---

## Dependencies between modules

```
solver-api       ← zero dependencies
     ↑
solver-core      ← depends on solver-api
     ↑
solver-graph     ← depends on solver-api (test scope: solver-core)

solver-int       ← depends on solver-api (future)
```

Rule: **solver-core does not depend on solver-graph** (and vice versa in compile scope).
Algorithms operate on interfaces, not on implementations.
