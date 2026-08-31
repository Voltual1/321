import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.protobuf)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "me.voltual.a321"
    compileSdk = 37 // 升级至 37

    // AGP 9.0 基础包名/归档名设置
    base {
        archivesName.set("A321")
    }

    defaultConfig {
        applicationId = "me.voltual.a321"
        minSdk = 21
        targetSdk = 37 // 升级至 37
        versionCode = 5
        versionName = "2.2"

        multiDexEnabled = true
        buildConfigField("String", "LICENSE", "\"GPLv3\"")
        // 此处删除了旧的 androidResources
    }

    // AGP 9.0 替代 resourceConfigurations 的新写法
    androidResources {
        localeFilters += "zh"
    }

    signingConfigs {
        create("release") {
            storeFile = file(
                System.getenv("KEYSTORE_PATH")
                    ?: keystoreProperties.getProperty("storeFile")
                    ?: "debug.keystore"
            )
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: keystoreProperties.getProperty("storePassword")
            keyAlias = System.getenv("KEY_ALIAS") ?: keystoreProperties.getProperty("keyAlias")
            keyPassword = System.getenv("KEY_PASSWORD") ?: keystoreProperties.getProperty("keyPassword")
        }
    }

    // 移除了 AGP 9 不再支持的 applicationVariants 变量重命名块

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
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
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
            excludes.add("/META-INF/INDEX.LIST")
            excludes.add("/META-INF/DEPENDENCIES")
            excludes.add("/META-INF/LICENSE*")
            excludes.add("/META-INF/*.txt")
            excludes.add("/google/protobuf/**")
            excludes.add("/src/google/protobuf/**")
            excludes.add("/java/core/java_features_proto-descriptor-set.proto.bin")
            excludes.add("DebugProbesKt.bin")
            merges.add("/META-INF/services/**")
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
  
  implementation("androidx.navigation:navigation-compose:2.9.6")  
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
  
  implementation(libs.zxing.core)

  implementation(libs.koin.core)
  implementation(libs.koin.android.compose)
  implementation(libs.koin.startup)

  implementation(libs.coil.compose)
  implementation(libs.coil.network.ktor)
  implementation(libs.markdown)
  implementation(libs.simple.storage)
  implementation(libs.tink.android)
  implementation(libs.protobuf.kotlin)
}

protobuf {
  protoc { artifact = libs.protoc.artifact.get().toString() }
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
        freeCompilerArgs.add("-XXLanguage:+ExplicitBackingFields")
    }
}