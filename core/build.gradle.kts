plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.gdx)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Um teste travado falha em vez de prender o build.
    systemProperty("junit.jupiter.execution.timeout.default", "30 s")
    systemProperty("junit.jupiter.execution.timeout.thread.mode.default", "SEPARATE_THREAD")
    testLogging { events("passed", "failed", "skipped") }
}
