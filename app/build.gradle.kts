import java.util.Properties
import com.android.build.api.variant.FilterConfiguration

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

// 读取 Keystore 配置
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
        resourceConfigurations += listOf("zh")
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: keystoreProperties.getProperty("storeFile") ?: "debug.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: keystoreProperties.getProperty("storePassword")
            keyAlias = System.getenv("KEY_ALIAS") ?: keystoreProperties.getProperty("keyAlias")
            keyPassword = System.getenv("KEY_PASSWORD") ?: keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("release")
        }
    }

    @Suppress("UnstableApiUsage")
    androidComponents {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                val abi = output.filters.find { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier ?: "universal"
                output.outputFileName.set("A321-${variant.outputs.first().versionName.get()}-$abi-${variant.name}.apk")
            }
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
    // JDK Desugaring
    coreLibraryDesugaring(libs.android.desugar)

    // Kotlin 基础
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.datetime)

    // Compose 核心
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation3 与 自适应
    implementation(libs.compose.navigation3)
    implementation(libs.compose.navigation3.ui)
    implementation(libs.viewmodel.navigation3)
    implementation(libs.compose.adaptive)
    implementation(libs.compose.adaptive.layout)
    implementation(libs.compose.adaptive.navigation)

    // 网络与存储
    implementation(libs.okhttp)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.datastore.core)

    // 依赖注入
    implementation(libs.koin.core)
    implementation(libs.koin.android.compose)
    implementation(libs.koin.workmanager)
    implementation(libs.koin.startup)
    implementation(libs.koin.annotations)
    ksp(libs.koin.ksp.compiler)

    // UI 组件与多媒体
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

    // 系统工具与安全
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
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // 显式备用字段
        freeCompilerArgs.add("-XXLanguage:+ExplicitBackingFields")
    }
}