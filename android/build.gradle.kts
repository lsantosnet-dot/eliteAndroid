plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.eliteandroid.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eliteandroid"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-etapa1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets["main"].jniLibs.srcDirs("libs")
}

// Bibliotecas nativas do LibGDX (.so) para cada ABI, extraídas para android/libs/<abi>/.
val natives: Configuration by configurations.creating

dependencies {
    implementation(project(":core"))
    implementation(libs.gdx.backend.android)
    implementation(libs.androidx.appcompat)

    listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64").forEach { abi ->
        natives(variantOf(libs.gdx.platform) { classifier("natives-$abi") })
    }
}

val copyAndroidNatives by tasks.registering {
    val nativeJars = natives
    val libsDir = layout.projectDirectory.dir("libs")
    inputs.files(nativeJars)
    outputs.dir(libsDir)
    doLast {
        nativeJars.files.forEach { jar ->
            val abi = jar.nameWithoutExtension.substringAfterLast("natives-")
            val outDir = libsDir.dir(abi).asFile.apply { mkdirs() }
            project.copy {
                from(project.zipTree(jar))
                into(outDir)
                include("*.so")
            }
        }
    }
}

tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn(copyAndroidNatives)
}
