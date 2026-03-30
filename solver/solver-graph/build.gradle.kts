dependencies {
    implementation(project(":solver-api"))

    // tests use solver-core for end-to-end tests
    testImplementation(project(":solver-core"))
}
