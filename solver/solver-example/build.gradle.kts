plugins {
    application
}

dependencies {
    implementation(project(":solver-api"))
    implementation(project(":solver-core"))
    implementation(project(":solver-graph"))
}

application {
    mainClass.set(providers.gradleProperty("mainClass")
            .orElse("pl.cezarysanecki.solver.example.DijkstraExample"))
}
