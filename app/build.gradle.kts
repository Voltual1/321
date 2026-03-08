import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.shizuku.refine)
    id("kotlin-parcelize")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "me.voltual.a321"
    compileSdk = 36

    defaultConfig {
        applicationId = "me.voltual.a321"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        
        multiDexEnabled = true
        buildConfigField("String", "LICENSE", "\"GPLv3\"")        
        androidResources {
            localeFilters += "zh"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: keystoreProperties.getProperty("storeFile") ?: "debug.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: keystoreProperties.getProperty("storePassword")
            keyAlias = System.getenv("KEY_ALIAS") ?: keystoreProperties.getProperty("keyAlias")
            keyPassword = System.getenv("KEY_PASSWORD") ?: keystoreProperties.getProperty("keyPassword")
        }
    }    

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            val abi = output.getFilter(com.android.build.OutputFile.ABI) ?: "universal"
            output.outputFileName = "A321-${variant.versionName}-$abi-${variant.buildType.name}.apk"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/*.txt",
                "/google/protobuf/**",
                "/src/google/protobuf/**",
                "/java/core/java_features_proto-descriptor-set.proto.bin",
                "DebugProbesKt.bin"
            )
            merges += "/META-INF/services/**"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.android.desugar)    
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.datetime)

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.compose.navigation3)
    implementation(libs.compose.navigation3.ui)
    implementation(libs.viewmodel.navigation3)
    implementation(libs.compose.adaptive)
    implementation(libs.compose.adaptive.layout)
    implementation(libs.compose.adaptive.navigation)

    implementation(libs.okhttp)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.logging)
    
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    
    implementation(libs.androidx.datastore.preferences) 
    implementation(libs.androidx.datastore.core)        

    implementation(libs.koin.core)
    implementation(libs.koin.android.compose)
    implementation(libs.koin.workmanager)
    implementation(libs.koin.startup)
    implementation(libs.koin.annotations)
    ksp(libs.koin.ksp.compiler)

    implementation(libs.google.material)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.biometric)
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor)
    implementation(libs.photoview)
    implementation(libs.imagepicker)
    implementation(libs.markdown)
    implementation(libs.zxing.core)
    implementation(libs.compose.html.converter)
    implementation(libs.ijkplayer)
    implementation(project(":DanmakuFlameMaster"))

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.shizuku.refine.runtime)
    compileOnly(libs.shizuku.hidden)
    implementation(libs.libsu.core)
    implementation(libs.simple.storage)
    implementation(libs.tink.android)
    implementation(libs.protobuf.kotlin)
    implementation(libs.androidx.work.runtime)
}

protobuf {
    protoc {
        artifact = libs.protoc.artifact.get().toString()
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java")
                create("kotlin")
            }
            //不建议用lite，别问为什么，等R8混淆后，发现lite是反射你就知道了。
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-XXLanguage:+ExplicitBackingFields")
    }
}