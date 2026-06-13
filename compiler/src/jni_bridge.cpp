#include <jni.h>
#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>

#include "sc.h"

#define LOG_TAG "GSCompiler"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* Buffer for capturing stdout/stderr */
#define OUTPUT_BUF_SIZE (1024 * 1024)
static char g_output_buf[OUTPUT_BUF_SIZE];
static int g_output_pos = 0;
static pthread_mutex_t g_output_mutex = PTHREAD_MUTEX_INITIALIZER;

/* Callback to Java for streaming output */
static JavaVM *g_jvm = NULL;
static jclass g_callback_class = NULL;
static jobject g_callback_obj = NULL;
static jmethodID g_on_output_method = NULL;

/* Redirect stdout to our buffer */
static FILE *g_orig_stdout = NULL;
static FILE *g_orig_stderr = NULL;

static int g_output_capture_fd[2] = {-1, -1};
static int g_error_capture_fd[2] = {-1, -1};

static void pipe_stdout_to_buffer(void) {
    if (pipe(g_output_capture_fd) != 0) {
        LOGE("Failed to create stdout pipe");
        return;
    }
    if (pipe(g_error_capture_fd) != 0) {
        LOGE("Failed to create stderr pipe");
        close(g_output_capture_fd[0]);
        close(g_output_capture_fd[1]);
        return;
    }

    fflush(stdout);
    g_orig_stdout = stdout;
    stdout = fdopen(g_output_capture_fd[1], "w");
    if (stdout != NULL) {
        setvbuf(stdout, NULL, _IONBF, 0);
    }

    fflush(stderr);
    g_orig_stderr = stderr;
    stderr = fdopen(g_error_capture_fd[1], "w");
    if (stderr != NULL) {
        setvbuf(stderr, NULL, _IONBF, 0);
    }
}

static void restore_stdout(void) {
    if (g_orig_stdout != NULL) {
        fflush(stdout);
        fclose(stdout);
        stdout = g_orig_stdout;
        g_orig_stdout = NULL;
    }
    if (g_orig_stderr != NULL) {
        fflush(stderr);
        fclose(stderr);
        stderr = g_orig_stderr;
        g_orig_stderr = NULL;
    }
    if (g_output_capture_fd[0] >= 0) {
        close(g_output_capture_fd[0]);
        g_output_capture_fd[0] = -1;
    }
    if (g_output_capture_fd[1] >= 0) {
        close(g_output_capture_fd[1]);
        g_output_capture_fd[1] = -1;
    }
    if (g_error_capture_fd[0] >= 0) {
        close(g_error_capture_fd[0]);
        g_error_capture_fd[0] = -1;
    }
    if (g_error_capture_fd[1] >= 0) {
        close(g_error_capture_fd[1]);
        g_error_capture_fd[1] = -1;
    }
}

static JNIEnv *get_jni_env(void) {
    JNIEnv *env;
    int status = (*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        status = (*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL);
        if (status != JNI_OK) {
            LOGE("Failed to attach thread to JVM");
            return NULL;
        }
    }
    return env;
}

static void send_output_to_java(const char *text, int is_error) {
    if (g_callback_obj == NULL || g_on_output_method == NULL)
        return;

    JNIEnv *env = get_jni_env();
    if (env == NULL) return;

    jstring jtext = (*env)->NewStringUTF(env, text);
    if (jtext == NULL) return;

    (*env)->CallVoidMethod(env, g_callback_obj, g_on_output_method, jtext, is_error);
    (*env)->DeleteLocalRef(env, jtext);
}

/* Thread that reads from the pipes and sends to Java */
static void *output_reader_thread(void *arg) {
    (void)arg;
    char buf[4096];

    /* Set non-blocking on read ends */
    int flags;
    flags = fcntl(g_output_capture_fd[0], F_GETFL, 0);
    fcntl(g_output_capture_fd[0], F_SETFL, flags | O_NONBLOCK);
    flags = fcntl(g_error_capture_fd[0], F_GETFL, 0);
    fcntl(g_error_capture_fd[0], F_SETFL, flags | O_NONBLOCK);

    for (;;) {
        int n;
        int had_data = 0;

        n = (int)read(g_output_capture_fd[0], buf, sizeof(buf) - 1);
        if (n > 0) {
            buf[n] = '\0';
            send_output_to_java(buf, 0);
            had_data = 1;
        }

        n = (int)read(g_error_capture_fd[0], buf, sizeof(buf) - 1);
        if (n > 0) {
            buf[n] = '\0';
            send_output_to_java(buf, 1);
            had_data = 1;
        }

        if (!had_data) {
            /* Check if pipe is still open */
            if (g_output_capture_fd[1] < 0 && g_error_capture_fd[1] < 0)
                break;
            usleep(10000); /* 10ms */
        }
    }

    return NULL;
}

/* ============================================================
 * JNI Functions
 * ============================================================ */

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    g_jvm = vm;
    LOGI("GS Compiler native library loaded");
    return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM *vm, void *reserved) {
    (void)vm;
    (void)reserved;
    LOGI("GS Compiler native library unloaded");
}

/*
 * Class:     com_mehdigm_compiler_compiler_NativeCompiler
 * Method:    nativeCompile
 * Signature: (Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Lcom/mehdigm/compiler/compiler/CompilationCallback;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_mehdigm_compiler_compiler_NativeCompiler_nativeCompile(
    JNIEnv *env,
    jobject thiz,
    jstring inputPath,
    jstring outputPath,
    jobjectArray includePaths,
    jobject callback)
{
    (void)thiz;

    const char *infile = (*env)->GetStringUTFChars(env, inputPath, NULL);
    const char *outfile = (*env)->GetStringUTFChars(env, outputPath, NULL);

    if (infile == NULL || outfile == NULL) {
        LOGE("Failed to get string UTF chars");
        if (infile) (*env)->ReleaseStringUTFChars(env, inputPath, infile);
        return JNI_FALSE;
    }

    LOGI("Compiling: %s -> %s", infile, outfile);

    /* Setup callback */
    if (callback != NULL) {
        g_callback_obj = (*env)->NewGlobalRef(env, callback);
        jclass cbClass = (*env)->GetObjectClass(env, callback);
        g_on_output_method = (*env)->GetMethodID(env, cbClass, "onOutput", "(Ljava/lang/String;Z)V");
        if (g_on_output_method == NULL) {
            LOGE("Failed to find onOutput method in callback");
        }
    }

    /* Setup include paths */
    const char *includes[MAX_INCLUDE_DEPTH];
    int num_includes = 0;
    memset(includes, 0, sizeof(includes));

    if (includePaths != NULL) {
        jsize len = (*env)->GetArrayLength(env, includePaths);
        for (jsize i = 0; i < len && num_includes < MAX_INCLUDE_DEPTH; i++) {
            jstring jpath = (jstring)(*env)->GetObjectArrayElement(env, includePaths, i);
            if (jpath != NULL) {
                includes[num_includes] = (*env)->GetStringUTFChars(env, jpath, NULL);
                num_includes++;
                (*env)->DeleteLocalRef(env, jpath);
            }
        }
    }

    /* Reset output buffer */
    pthread_mutex_lock(&g_output_mutex);
    g_output_pos = 0;
    memset(g_output_buf, 0, sizeof(g_output_buf));
    pthread_mutex_unlock(&g_output_mutex);

    /* Reset compiler state */
    g_total_errors = 0;
    g_total_warnings = 0;

    /* Start pipe and reader thread for output streaming */
    pipe_stdout_to_buffer();

    pthread_t reader_thread;
    pthread_create(&reader_thread, NULL, output_reader_thread, NULL);

    /* Compile */
    char error_buf[4096] = {0};
    int result = pc_compile(infile, outfile, includes, num_includes, error_buf, sizeof(error_buf));

    /* Restore stdout/stderr */
    restore_stdout();

    /* Wait for reader thread to finish */
    pthread_join(reader_thread, NULL);

    /* Send remaining error buffer */
    if (error_buf[0]) {
        send_output_to_java(error_buf, 1);
    }

    /* Send compilation result */
    char result_msg[256];
    if (result) {
        snprintf(result_msg, sizeof(result_msg),
            "\n=== Compilation successful ===\nOutput: %s (%d errors, %d warnings)\n",
            outfile, g_total_errors, g_total_warnings);
        send_output_to_java(result_msg, 0);
    } else {
        snprintf(result_msg, sizeof(result_msg),
            "\n=== Compilation failed ===\nErrors: %d, Warnings: %d\n",
            g_total_errors, g_total_warnings);
        send_output_to_java(result_msg, 1);
    }

    /* Cleanup */
    (*env)->ReleaseStringUTFChars(env, inputPath, infile);
    (*env)->ReleaseStringUTFChars(env, outputPath, outfile);

    for (int i = 0; i < num_includes; i++) {
        if (includes[i] != NULL) {
            /* We don't release strings since we stored pointer only */
        }
    }

    if (g_callback_obj != NULL) {
        (*env)->DeleteGlobalRef(env, g_callback_obj);
        g_callback_obj = NULL;
        g_on_output_method = NULL;
    }

    return result ? JNI_TRUE : JNI_FALSE;
}
