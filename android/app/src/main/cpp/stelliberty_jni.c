#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include "libmihomo.h"

static char *jstring_to_cstr(JNIEnv *env, jstring s) {
    if (!s) return NULL;
    const char *tmp = (*env)->GetStringUTFChars(env, s, NULL);
    if (!tmp) return NULL;
    char *copy = strdup(tmp);
    (*env)->ReleaseStringUTFChars(env, s, tmp);
    return copy;
}

static jstring go_cstr_to_jstring(JNIEnv *env, char *s) {
    if (!s) return NULL;
    jstring out = (*env)->NewStringUTF(env, s);
    stellibertyFreeString(s);
    return out;
}

JNIEXPORT void JNICALL
Java_com_stelliberty_android_data_bridge_StellibertyCoreBridge_nativeCoreInit(
        JNIEnv *env, jclass clazz, jstring jHomeDir, jstring jUserAgent) {
    char *homeDir = jstring_to_cstr(env, jHomeDir);
    char *userAgent = jstring_to_cstr(env, jUserAgent);
    stellibertyCoreInit(homeDir ? homeDir : "", userAgent ? userAgent : "");
    free(homeDir);
    free(userAgent);
}

JNIEXPORT jstring JNICALL
Java_com_stelliberty_android_data_bridge_StellibertyCoreBridge_nativeFetchAndValid(
        JNIEnv *env, jclass clazz,
        jstring jWorkDir, jstring jUrl, jboolean jForce, jstring jHttpProxy, jstring jUserAgent,
        jint jToken) {
    char *workDir = jstring_to_cstr(env, jWorkDir);
    char *url = jstring_to_cstr(env, jUrl);
    char *httpProxy = jstring_to_cstr(env, jHttpProxy);
    char *userAgent = jstring_to_cstr(env, jUserAgent);

    char *result = stellibertyFetchAndValid(
            workDir ? workDir : "",
            url ? url : "",
            jForce ? 1 : 0,
            httpProxy ? httpProxy : "",
            userAgent ? userAgent : "",
            (int) jToken);

    free(workDir);
    free(url);
    free(httpProxy);
    free(userAgent);

    return go_cstr_to_jstring(env, result);
}

JNIEXPORT void JNICALL
Java_com_stelliberty_android_data_bridge_StellibertyCoreBridge_nativeCancel(
        JNIEnv *env, jclass clazz, jint jToken) {
    stellibertyCancel((int) jToken);
}

JNIEXPORT jstring JNICALL
Java_com_stelliberty_android_data_bridge_StellibertyCoreBridge_nativeQueryProgress(
        JNIEnv *env, jclass clazz, jint jToken) {
    char *progress = stellibertyQueryProgress((int) jToken);
    return go_cstr_to_jstring(env, progress);
}

JNIEXPORT void JNICALL
Java_com_stelliberty_android_data_bridge_StellibertyCoreBridge_nativeSetAgeSecretKey(
        JNIEnv *env, jclass clazz, jstring jKey) {
    char *key = jstring_to_cstr(env, jKey);
    stellibertySetAgeSecretKey(key ? key : "");
    free(key);
}

JNIEXPORT jstring JNICALL
Java_com_stelliberty_android_data_bridge_StellibertyCoreBridge_nativeDecryptFile(
        JNIEnv *env, jclass clazz, jstring jSource, jstring jTarget, jstring jKey) {
    char *source = jstring_to_cstr(env, jSource);
    char *target = jstring_to_cstr(env, jTarget);
    char *key = jstring_to_cstr(env, jKey);
    char *result = stellibertyDecryptFile(source ? source : "", target ? target : "", key ? key : "");
    free(source);
    free(target);
    free(key);
    return go_cstr_to_jstring(env, result);
}

JNIEXPORT jstring JNICALL
Java_com_stelliberty_android_data_bridge_StellibertyCoreBridge_nativeValidateTransform(
        JNIEnv *env, jclass clazz, jstring jWorkDir, jstring jTransform, jstring jKey) {
    char *workDir = jstring_to_cstr(env, jWorkDir);
    char *transform = jstring_to_cstr(env, jTransform);
    char *key = jstring_to_cstr(env, jKey);
    char *result = stellibertyValidateTransform(workDir ? workDir : "", transform ? transform : "", key ? key : "");
    free(workDir);
    free(transform);
    free(key);
    return go_cstr_to_jstring(env, result);
}

JNIEXPORT jstring JNICALL
Java_com_stelliberty_android_data_bridge_StellibertyCoreBridge_nativeChainProxyContext(
        JNIEnv *env, jclass clazz, jstring jWorkDir, jstring jTransform, jstring jKey) {
    char *workDir = jstring_to_cstr(env, jWorkDir);
    char *transform = jstring_to_cstr(env, jTransform);
    char *key = jstring_to_cstr(env, jKey);
    char *result = stellibertyChainProxyContext(workDir ? workDir : "", transform ? transform : "", key ? key : "");
    free(workDir);
    free(transform);
    free(key);
    return go_cstr_to_jstring(env, result);
}

JNIEXPORT jstring JNICALL
Java_com_stelliberty_android_data_bridge_StellibertyCoreBridge_nativeRuleContext(
        JNIEnv *env, jclass clazz, jstring jWorkDir, jstring jTransform, jstring jKey) {
    char *workDir = jstring_to_cstr(env, jWorkDir);
    char *transform = jstring_to_cstr(env, jTransform);
    char *key = jstring_to_cstr(env, jKey);
    char *result = stellibertyRuleContext(workDir ? workDir : "", transform ? transform : "", key ? key : "");
    free(workDir);
    free(transform);
    free(key);
    return go_cstr_to_jstring(env, result);
}

JNIEXPORT jstring JNICALL
Java_com_stelliberty_android_data_bridge_StellibertyCoreBridge_nativeGenAgeKeyPair(
        JNIEnv *env, jclass clazz) {
    char *result = stellibertyGenAgeKeyPair();
    return go_cstr_to_jstring(env, result);
}

JNIEXPORT jstring JNICALL
Java_com_stelliberty_android_data_bridge_StellibertyCoreBridge_nativeGenAgeHybridKeyPair(
        JNIEnv *env, jclass clazz) {
    char *result = stellibertyGenAgeHybridKeyPair();
    return go_cstr_to_jstring(env, result);
}
