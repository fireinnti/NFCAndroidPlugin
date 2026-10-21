plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// TODO: Update value to your plugin's name.
val pluginName = "GodotAndroidNFCPlugin"

// TODO: Update value to match your plugin's package name.
val pluginPackageName = "org.godotengine.plugin.android.nfc"

android {
    namespace = pluginPackageName
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 24

        manifestPlaceholders["godotPluginName"] = pluginName
        manifestPlaceholders["godotPluginPackageName"] = pluginPackageName
        buildConfigField("String", "GODOT_PLUGIN_NAME", "\"${pluginName}\"")
        setProperty("archivesBaseName", pluginName)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    compileOnly("org.godotengine:godot:4.7.2.stable")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.godotengine:godot:4.7.2.stable")
    // TODO: Additional dependencies should be added to export_plugin.gd as well.
}

android.testOptions.unitTests.isIncludeAndroidResources = true

// Package only after both variants have built successfully.
val packagePlugin by tasks.registering(Copy::class) {
    dependsOn("assembleDebug", "assembleRelease")
    from("export_scripts_template")
    from("build/outputs/aar/$pluginName-debug.aar") { into("bin/debug") }
    from("build/outputs/aar/$pluginName-release.aar") { into("bin/release") }
    into("demo/addons/$pluginName")
}

tasks.named("assemble") { finalizedBy(packagePlugin) }
