pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "EliteAndroid"

// :core é Kotlin/JVM puro (lógica do jogo + LibGDX core) e compila/testa sem Android SDK.
include(":core")

// :android só é incluído quando há um Android SDK disponível (ANDROID_HOME / local.properties).
// Isso permite rodar `./gradlew :core:test` em CI ou máquinas sem SDK.
val localProps = file("local.properties")
val hasSdk = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    (localProps.exists() && localProps.readText().contains("sdk.dir"))
if (hasSdk) {
    include(":android")
} else {
    logger.lifecycle("Android SDK não encontrado: módulo :android ignorado (apenas :core será configurado).")
}
