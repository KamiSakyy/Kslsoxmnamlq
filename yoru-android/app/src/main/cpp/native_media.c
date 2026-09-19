#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>

#define KEY_LEN 18

// Mask components (split so no contiguous key or pattern exists)
static const unsigned char M_A[KEY_LEN] = {
    0x54, 0x32, 0x11, 0x78, 0x22, 0x65, 0x43, 0x19,
    0x88, 0x71, 0x24, 0x90, 0x55, 0x17, 0x33, 0x44, 0x55, 0x66
};
static const unsigned char M_B[KEY_LEN] = {
    0x5a, 0x1b, 0x3e, 0x5b, 0x0d, 0x6c, 0x7c, 0x20,
    0xa0, 0x4e, 0x0a, 0x81, 0x6a, 0x34, 0x5b, 0x2e, 0x3d, 0x0a
};

// Obfuscated root paths (XORed with 0x27)
static const unsigned char P_SU1[] = { 0x08, 0x54, 0x5e, 0x54, 0x53, 0x42, 0x4a, 0x08, 0x45, 0x4e, 0x49, 0x08, 0x54, 0x52, 0x00 };
static const unsigned char P_SU2[] = { 0x08, 0x54, 0x5e, 0x54, 0x53, 0x42, 0x4a, 0x08, 0x5f, 0x45, 0x4e, 0x49, 0x08, 0x54, 0x52, 0x00 };
static const unsigned char P_SU3[] = { 0x08, 0x54, 0x45, 0x4e, 0x49, 0x08, 0x54, 0x52, 0x00 };
static const unsigned char P_FR1[] = { 0x08, 0x43, 0x46, 0x53, 0x46, 0x08, 0x4b, 0x48, 0x44, 0x46, 0x4b, 0x08, 0x53, 0x4a, 0x57, 0x08, 0x41, 0x55, 0x4e, 0x43, 0x46, 0x0a, 0x54, 0x42, 0x55, 0x51, 0x42, 0x55, 0x00 };

static void __attribute__((noinline)) derive_key(unsigned char *out) {
    volatile unsigned char v = 0x5a;
    for (int i = 0; i < KEY_LEN; i++) {
        out[i] = (unsigned char)(M_A[i] ^ M_B[i] ^ v);
    }
}

static void decode_path(const unsigned char *src, char *dst) {
    volatile unsigned char key = 0x27;
    int i = 0;
    while (src[i] != 0x00) {
        dst[i] = (char)(src[i] ^ key);
        i++;
    }
    dst[i] = '\0';
}

static int hex_val(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

static jstring JNICALL
native_decrypt(JNIEnv *env, jclass clazz, jstring hexStr) {
    (void)clazz;
    if (!hexStr) return NULL;
    const char *chars = (*env)->GetStringUTFChars(env, hexStr, NULL);
    if (!chars) return NULL;

    size_t in_len = strlen(chars);
    char *clean = (char *)malloc(in_len + 1);
    if (!clean) {
        (*env)->ReleaseStringUTFChars(env, hexStr, chars);
        return NULL;
    }
    size_t clean_len = 0;
    for (size_t i = 0; i < in_len; i++) {
        if (chars[i] != '-') {
            clean[clean_len++] = chars[i];
        }
    }
    clean[clean_len] = '\0';
    (*env)->ReleaseStringUTFChars(env, hexStr, chars);

    if (clean_len == 0 || (clean_len % 2) != 0) {
        free(clean);
        return (*env)->NewStringUTF(env, "");
    }

    size_t out_len = clean_len / 2;
    unsigned char *buf = (unsigned char *)malloc(out_len + 1);
    if (!buf) {
        free(clean);
        return NULL;
    }
    unsigned char key[KEY_LEN];
    derive_key(key);

    for (size_t i = 0; i < out_len; i++) {
        int h = hex_val(clean[i * 2]);
        int l = hex_val(clean[i * 2 + 1]);
        if (h < 0 || l < 0) {
            free(clean);
            free(buf);
            return (*env)->NewStringUTF(env, "");
        }
        unsigned char b = (unsigned char)((h << 4) | l);
        buf[i] = (unsigned char)(b ^ key[i % KEY_LEN]);
    }
    buf[out_len] = '\0';
    free(clean);

    jstring result = (*env)->NewStringUTF(env, (const char *)buf);
    free(buf);
    return result;
}

static jboolean JNICALL
native_check(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    char path[64];
    const unsigned char *enc_paths[] = { P_SU1, P_SU2, P_SU3, P_FR1, NULL };
    for (int i = 0; enc_paths[i] != NULL; i++) {
        decode_path(enc_paths[i], path);
        struct stat st;
        if (stat(path, &st) == 0) {
            return JNI_FALSE;
        }
    }
    return JNI_TRUE;
}

static const JNINativeMethod gMethods[] = {
    { "x", "(Ljava/lang/String;)Ljava/lang/String;", (void *)native_decrypt },
    { "c", "()Z", (void *)native_check }
};

__attribute__((visibility("default")))
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_VERSION_1_6;
    }
    jclass cls = (*env)->FindClass(env, "com/tsuyu/line/Sec");
    if (cls != NULL) {
        (*env)->RegisterNatives(env, cls, gMethods, sizeof(gMethods) / sizeof(gMethods[0]));
        (*env)->DeleteLocalRef(env, cls);
    }
    return JNI_VERSION_1_6;
}
