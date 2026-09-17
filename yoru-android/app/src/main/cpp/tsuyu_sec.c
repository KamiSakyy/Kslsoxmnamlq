#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>

#define KEY_LEN 18

static const unsigned char MASK_SEED[KEY_LEN] = {
    0x9b, 0xbc, 0xba, 0xb6, 0xba, 0x9c, 0xaa, 0xac,
    0xbd, 0xaa, 0xbb, 0x84, 0xaa, 0xb6, 0xfd, 0xff, 0xfd, 0xf9
};

static void derive_key(unsigned char *out) {
    for (int i = 0; i < KEY_LEN; i++) {
        out[i] = (unsigned char)(MASK_SEED[i] ^ 0xcf);
    }
}

static int hex_val(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

JNIEXPORT jbyteArray JNICALL
Java_com_tsuyu_line_Sec_nativeKey(JNIEnv *env, jclass clazz) {
    (void)clazz;
    unsigned char k[KEY_LEN];
    derive_key(k);
    jbyteArray arr = (*env)->NewByteArray(env, KEY_LEN);
    if (!arr) return NULL;
    (*env)->SetByteArrayRegion(env, arr, 0, KEY_LEN, (const jbyte *)k);
    return arr;
}

JNIEXPORT jstring JNICALL
Java_com_tsuyu_line_Sec_nativeDecryptStr(JNIEnv *env, jclass clazz, jstring hexStr) {
    (void)clazz;
    if (!hexStr) return NULL;
    const char *chars = (*env)->GetStringUTFChars(env, hexStr, NULL);
    if (!chars) return NULL;

    size_t in_len = strlen(chars);
    char *clean = (char *)malloc(in_len + 1);
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

JNIEXPORT jboolean JNICALL
Java_com_tsuyu_line_Sec_nativeSecurityCheck(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    // Check for common root binaries
    const char *su_paths[] = {
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/data/local/tmp/frida-server",
        NULL
    };
    for (int i = 0; su_paths[i] != NULL; i++) {
        struct stat st;
        if (stat(su_paths[i], &st) == 0) {
            return JNI_FALSE;
        }
    }
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)vm;
    (void)reserved;
    return JNI_VERSION_1_6;
}
