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
