plugins {
    alias(libs.plugins.android.application)
}

val cfgTargetPackage: String = providers.gradleProperty("target.package").get()
val cfgModuleId: String = providers.gradleProperty("module.id").get()
val cfgModuleName: String = providers.gradleProperty("module.name").get()
val cfgModuleAuthor: String = providers.gradleProperty("module.author").get()
val cfgModuleDescription: String = providers.gradleProperty("module.description").get()
val cfgXposedApiMin: Int = providers.gradleProperty("xposed.api.min").get().toInt()
val cfgXposedApiTarget: Int = providers.gradleProperty("xposed.api.target").get().toInt()

android {
    namespace = "eu.hxreborn.gdialerdechip"
    compileSdk = 37

    defaultConfig {
        applicationId = cfgModuleId
        minSdk = 30
        targetSdk = 37

        versionCode = project.property("version.code").toString().toInt()
        versionName = project.property("version.name").toString()

        buildConfigField("String", "TARGET_PACKAGE", "\"$cfgTargetPackage\"")

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    @Suppress("UnstableApiUsage")
    androidResources {
        localeFilters += listOf("en")
    }

    lint {
        disable += "DataExtractionRules"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        create("release") {
            fun secret(name: String): String? =
                providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull

            val storeFilePath = secret("RELEASE_STORE_FILE")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = secret("RELEASE_STORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
            } else {
                logger.warn("RELEASE_STORE_FILE not found. Release signing is disabled.")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        jniLibs {
            // Keep libdexkit.so uncompressed in-APK so System.loadLibrary resolves it in the hooked process.
            useLegacyPackaging = false
        }
        resources {
            merges += listOf("META-INF/xposed/**")
            excludes +=
                listOf(
                    "META-INF/AL2.0",
                    "META-INF/LGPL2.1",
                    "META-INF/LICENSE*",
                    "META-INF/NOTICE*",
                    "META-INF/DEPENDENCIES",
                    "META-INF/*.version",
                    "META-INF/*.kotlin_module",
                    "kotlin/**",
                    "DebugProbesKt.bin",
                )
        }
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.dexkit)
}

abstract class GenerateXposedModuleProp : DefaultTask() {
    @get:Input
    abstract val moduleId: Property<String>

    @get:Input
    abstract val moduleName: Property<String>

    @get:Input
    abstract val moduleAuthor: Property<String>

    @get:Input
    abstract val moduleDescription: Property<String>

    @get:Input
    abstract val moduleVersionName: Property<String>

    @get:Input
    abstract val moduleVersionCode: Property<Int>

    @get:Input
    abstract val moduleMinApiVersion: Property<Int>

    @get:Input
    abstract val moduleTargetApiVersion: Property<Int>

    @get:Input
    abstract val targetPackage: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val target = outputDir.get().file("META-INF/xposed/module.prop").asFile
        target.parentFile.mkdirs()
        target.writeText(
            """
            id=${moduleId.get()}
            name=${moduleName.get()}
            version=${moduleVersionName.get()}
            versionCode=${moduleVersionCode.get()}
            author=${moduleAuthor.get()}
            description=${moduleDescription.get()}
            minApiVersion=${moduleMinApiVersion.get()}
            targetApiVersion=${moduleTargetApiVersion.get()}
            staticScope=true
            exceptionMode=protective
            """.trimIndent() + "\n",
        )

        outputDir.get().file("META-INF/xposed/scope.list").asFile.writeText(
            targetPackage.get() + "\n",
        )
    }
}

val generateXposedModuleProp by tasks.registering(GenerateXposedModuleProp::class) {
    moduleId.set(cfgModuleId)
    moduleName.set(cfgModuleName)
    moduleAuthor.set(cfgModuleAuthor)
    moduleDescription.set(cfgModuleDescription)
    moduleVersionName.set(project.property("version.name").toString())
    moduleVersionCode.set(project.property("version.code").toString().toInt())
    moduleMinApiVersion.set(cfgXposedApiMin)
    moduleTargetApiVersion.set(cfgXposedApiTarget)
    targetPackage.set(cfgTargetPackage)
}

androidComponents {
    onVariants { variant ->
        variant.sources.resources?.addGeneratedSourceDirectory(
            generateXposedModuleProp,
            GenerateXposedModuleProp::outputDir,
        )
    }
}
