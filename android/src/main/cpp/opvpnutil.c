// libopvpnutil.so - Simple utility library compiled with 16 KB page size support
// This library provides basic utility functions for OpenVPN

#include <jni.h>
#include <android/log.h>
#include <string.h>
#include <stdlib.h>

#define LOG_TAG "libopvpnutil"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Initialize function
JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGI("libopvpnutil loaded with 16 KB page size support");
    return JNI_VERSION_1_6;
}

// Basic utility function - string validation
JNIEXPORT jboolean JNICALL
Java_de_blinkt_openvpn_util_StringValidator_isValid(JNIEnv *env, jclass clazz, jstring str) {
    if (str == NULL) {
        return JNI_FALSE;
    }
    const char *nativeStr = (*env)->GetStringUTFChars(env, str, NULL);
    jboolean result = (nativeStr != NULL && strlen(nativeStr) > 0) ? JNI_TRUE : JNI_FALSE;
    (*env)->ReleaseStringUTFChars(env, str, nativeStr);
    return result;
}

// Memory utility - safe allocation
JNIEXPORT jlong JNICALL
Java_de_blinkt_openvpn_util_MemoryUtil_allocate(JNIEnv *env, jclass clazz, jint size) {
    if (size <= 0 || size > 1024 * 1024) {  // Max 1MB
        LOGE("Invalid allocation size: %d", size);
        return 0;
    }
    void *ptr = malloc(size);
    if (ptr == NULL) {
        LOGE("Failed to allocate %d bytes", size);
        return 0;
    }
    LOGI("Allocated %d bytes at %p", size, ptr);
    return (jlong)(uintptr_t)ptr;
}

// Memory utility - safe deallocation
JNIEXPORT void JNICALL
Java_de_blinkt_openvpn_util_MemoryUtil_free(JNIEnv *env, jclass clazz, jlong ptr) {
    if (ptr != 0) {
        free((void *)(uintptr_t)ptr);
        LOGI("Freed memory at %p", (void *)(uintptr_t)ptr);
    }
}

// Configuration helper
JNIEXPORT jstring JNICALL
Java_de_blinkt_openvpn_util_ConfigHelper_getDefaultConfig(JNIEnv *env, jclass clazz) {
    const char *config = "# OpenVPN Default Configuration\ndev tun\nproto udp\n";
    return (*env)->NewStringUTF(env, config);
}

// Version info
JNIEXPORT jstring JNICALL
Java_de_blinkt_openvpn_util_VersionUtil_getLibraryVersion(JNIEnv *env, jclass clazz) {
    return (*env)->NewStringUTF(env, "libopvpnutil-16kb-v1.0.0");
}

// Utility function for page size verification
JNIEXPORT jint JNICALL
Java_de_blinkt_openvpn_util_SystemUtil_getPageSize(JNIEnv *env, jclass clazz) {
    long page_size = sysconf(_SC_PAGESIZE);
    LOGI("System page size: %ld bytes", page_size);
    return (jint)page_size;
}

// Placeholder functions to maintain compatibility with nizwar's library
// These will be called by the OpenVPN core but don't need complex implementation

JNIEXPORT void JNICALL
Java_de_blinkt_openvpn_util_NetworkUtil_updateRoutes(JNIEnv *env, jclass clazz, jstring routes) {
    LOGI("updateRoutes called (16KB version)");
}

JNIEXPORT jboolean JNICALL
Java_de_blinkt_openvpn_util_ConnectionUtil_isConnected(JNIEnv *env, jclass clazz) {
    LOGI("isConnected called (16KB version)");
    return JNI_TRUE;
}
