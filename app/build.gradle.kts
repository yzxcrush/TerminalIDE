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
        versionCode = 20
        versionName = "1.13.2"

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
            // 关闭 R8 压缩：Chaquopy + R8 在部分机型会导致「release 才闪退」，
            // 先以可运行性优先，牺牲一点包体积。
            isMinifyEnabled = false
            isShrinkResources = false
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
        // Python 3.13：对 16KB 内存页设备（新机型 / ColorOS 16 常见）兼容性更好
        version = "3.13"

        // 构建期只预装基础包（均为纯 Python 包，不现场编译 C 扩展）。
        // 其余任意 PyPI 包在运行期动态安装到沙箱 site-packages。
        pip {
            install("pip")               // 运行期 pip install 的入口
            install("setuptools")
            install("wheel")
            install("requests")          // 网络请求
            install("beautifulsoup4")    // HTML/XML 解析
            install("tqdm")              // 进度条
            install("colorama")          // 彩色终端输出
            install("rich")              // 富文本 / 表格 / 高亮
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
