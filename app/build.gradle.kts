import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.chaquopy.python)
}

android {
    namespace = "com.terminalide"
    compileSdk = 35
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.terminalide"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // 只打 arm64-v8a（Python 3.12+ 仅支持 64 位）
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        // 构建期 NDK：编译 JNI 桥接库，演示 clang/clang++ 交叉编译
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 此处不配置签名：由 scripts/build_release.sh 用 apksigner 对
            // app-release-unsigned.apk 进行 zipalign + 签名。
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

chaquopy {
    defaultConfig {
        // Python 3.12：64 位、minSdk 24、arm64-v8a 均可满足。
        version = "3.12"

        // 构建期只预装基础包（均为纯 Python 包，不现场编译 C 扩展）。
        // 其余任意 PyPI 包在运行期动态安装到沙箱 site-packages。
        pip {
            install("pip")          // 运行期 pip install 的入口
            install("setuptools")
            install("wheel")
            install("requests")     // 基础网络库示例
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.security.crypto)
    implementation(libs.okhttp)

    debugImplementation(libs.androidx.ui.tooling)
}
