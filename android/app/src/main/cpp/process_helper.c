#include <jni.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <signal.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <android/log.h>

#define TAG "ProcessHelper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

JNIEXPORT jint JNICALL
Java_com_stelliberty_android_service_ProcessHelper_nativeForkExec(
        JNIEnv *env, jclass clazz,
        jstring jBinary, jobjectArray jArgs, jstring jWorkDir, jstring jLogFile) {

    const char *binary = (*env)->GetStringUTFChars(env, jBinary, NULL);
    const char *workDir = (*env)->GetStringUTFChars(env, jWorkDir, NULL);
    const char *logFile = jLogFile ? (*env)->GetStringUTFChars(env, jLogFile, NULL) : NULL;
    char **argv = NULL;
    int result = -1;
    int argc = 0;

    if (binary == NULL || workDir == NULL || (jLogFile != NULL && logFile == NULL)) {
        LOGE("GetStringUTFChars returned NULL");
        goto cleanup;
    }

    argc = (*env)->GetArrayLength(env, jArgs);
    argv = (char **) calloc(argc + 2, sizeof(char *));
    if (argv == NULL) {
        LOGE("calloc for argv failed");
        goto cleanup;
    }
    argv[0] = strdup(binary);
    if (argv[0] == NULL) {
        LOGE("strdup binary failed");
        goto cleanup;
    }
    for (int i = 0; i < argc; i++) {
        jstring jArg = (jstring) (*env)->GetObjectArrayElement(env, jArgs, i);
        if (jArg == NULL) {
            LOGE("null arg at %d", i);
            goto cleanup;
        }
        const char *arg = (*env)->GetStringUTFChars(env, jArg, NULL);
        if (arg != NULL) {
            argv[i + 1] = strdup(arg);
            (*env)->ReleaseStringUTFChars(env, jArg, arg);
        }
        (*env)->DeleteLocalRef(env, jArg);
        if (argv[i + 1] == NULL) {
            LOGE("strdup arg %d failed", i);
            goto cleanup;
        }
    }

    LOGI("fork+exec: %s, workDir=%s, logFile=%s", binary, workDir, logFile ? logFile : "(null)");

    pid_t pid = fork();

    if (pid == 0) {
        setsid();

        if (chdir(workDir) != 0) {
            _exit(126);
        }

        if (logFile) {
            int logFd = open(logFile, O_WRONLY | O_CREAT | O_TRUNC, 0644);
            if (logFd >= 0) {
                dup2(logFd, STDOUT_FILENO);
                dup2(logFd, STDERR_FILENO);
                close(logFd);
            }
        } else {
            dup2(STDERR_FILENO, STDOUT_FILENO);
        }

        execv(binary, argv);
        _exit(127);
    }

    if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
    } else {
        LOGI("child pid=%d", pid);
        result = pid;
    }

    cleanup:
    if (argv != NULL) {
        for (int i = 0; argv[i] != NULL; i++) {
            free(argv[i]);
        }
        free(argv);
    }
    if (binary != NULL) (*env)->ReleaseStringUTFChars(env, jBinary, binary);
    if (workDir != NULL) (*env)->ReleaseStringUTFChars(env, jWorkDir, workDir);
    if (logFile != NULL) (*env)->ReleaseStringUTFChars(env, jLogFile, logFile);

    return result;
}

static pid_t waitpid_eintr(pid_t pid, int *status, int options) {
    pid_t r;
    do {
        r = waitpid(pid, status, options);
    } while (r < 0 && errno == EINTR);
    return r;
}

JNIEXPORT void JNICALL
Java_com_stelliberty_android_service_ProcessHelper_nativeKill(
        JNIEnv *env, jclass clazz, jint pid, jboolean force) {
    if (pid > 0) {
        int sig = force ? SIGKILL : SIGTERM;
        LOGI("killing pid=%d sig=%d", pid, sig);
        kill((pid_t) pid, sig);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_stelliberty_android_service_ProcessHelper_nativeIsAlive(
        JNIEnv *env, jclass clazz, jint pid) {
    if (pid <= 0) return JNI_FALSE;
    int status = 0;
    return waitpid_eintr((pid_t) pid, &status, WNOHANG) == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_stelliberty_android_service_ProcessHelper_nativeWaitpid(
        JNIEnv *env, jclass clazz, jint pid, jint timeoutMs) {
    if (pid <= 0) return -1;
    const long stepUs = 10 * 1000;
    long remainingUs = (long) timeoutMs * 1000;
    for (;;) {
        int status = 0;
        pid_t r = waitpid_eintr((pid_t) pid, &status, WNOHANG);
        if (r == (pid_t) pid) {
            if (WIFEXITED(status)) return WEXITSTATUS(status);
            if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
            return -1;
        }
        if (r < 0) return -1;
        if (remainingUs <= 0) {
            LOGE("waitpid pid=%d timed out after %dms", pid, timeoutMs);
            return -1;
        }
        usleep(stepUs);
        remainingUs -= stepUs;
    }
}
