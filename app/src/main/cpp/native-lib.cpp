#include <jni.h>
#include <string>

// NDK clang/clang++ 交叉编译的 JNI 桥接示例（arm64-v8a）。
// 对应 Kotlin 侧 com.terminalide.core.NativeBridge#nativeVersion。
extern "C" JNIEXPORT jstring JNICALL
Java_com_terminalide_core_NativeBridge_nativeVersion(
        JNIEnv *env,
        jobject /* this */) {
    std::string info = "NDK clang/clang++ bridge (aarch64-linux-android)";
    return env->NewStringUTF(info.c_str());
}
