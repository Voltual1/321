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
        
        // 修复 Error 1: 替换废弃的 resourceConfigurations
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

    // 修复 Error 2: 修正 AGP 8.13+ 的 APK 重命名逻辑
    @Suppress("UnstableApiUsage")
    androidComponents {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                val abi = output.filters.find { 
                    it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI 
                }?.identifier ?: "universal"
                
                // 在新版本中，不再直接操作 outputFileName，而是通过底层任务进行映射
                // 或者通过这种兼容写法（确保 artifactName 正确）
                output.versionName.set(variant.outputs.first().versionName)
            }
        }
    }

    // 注意：如果上面的 androidComponents 逻辑在你的特定环境中仍有 Property 冲突，
    // 在 AGP 8.x 中最稳妥的重命名方式是使用下面的传统写法（虽然它被标注为过时，但它能绕过 Property 限制）
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

    // 修复 Error 3, 4, 5: 必须严格对应上一次梳理后的 libs.versions.toml 命名
    // 之前梳理的版本将 datetime 改为了 kotlinx.datetime，datastore 改为了 androidx.datastore
    
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.datetime) // 修正引用

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
    
    implementation(libs.androidx.datastore.preferences) // 修正引用
    implementation(libs.androidx.datastore.core)        // 修正引用

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
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-XXLanguage:+ExplicitBackingFields")
    }
}