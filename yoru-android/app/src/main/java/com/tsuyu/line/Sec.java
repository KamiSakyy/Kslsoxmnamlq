package com.tsuyu.line;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

public final class Sec {
    private static volatile boolean nativeLoaded;
    private static final byte[] K = initK();

    static {
        try {
            System.loadLibrary("c++_shared");
            nativeLoaded = true;
        } catch (Throwable ignored) {
            nativeLoaded = false;
        }
    }

    private static native String x(String hex);
    private static native boolean c();

    private static byte[] initK() {
        int[] s = new int[]{0x9b, 0xbc, 0xba, 0xb6, 0xba, 0x9c, 0xaa, 0xac, 0xbd, 0xaa, 0xbb, 0x84, 0xaa, 0xb6, 0xfd, 0xff, 0xfd, 0xf9};
        byte[] k = new byte[s.length];
        for (int i = 0; i < s.length; i++) {
            k[i] = (byte) (s[i] ^ 0xcf);
        }
        return k;
    }

    private Sec() {}

    public static String s(String hex) {
        if (hex == null || hex.isEmpty()) return "";
        if (nativeLoaded) {
            try {
                String res = x(hex);
                if (res != null && !res.isEmpty()) return res;
            } catch (Throwable ignored) {}
        }
        try {
            String clean = hex.replace("-", "");
            int len = clean.length();
            if ((len % 2) != 0) return "";
            byte[] out = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                out[i / 2] = (byte) ((Character.digit(clean.charAt(i), 16) << 4)
                        + Character.digit(clean.charAt(i + 1), 16));
            }
            for (int i = 0; i < out.length; i++) {
                out[i] ^= K[i % K.length];
            }
            return new String(out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    public static byte[] decryptData(byte[] encrypted) {
        if (encrypted == null || encrypted.length == 0) return new byte[0];
        try {
            byte[] xored = new byte[encrypted.length];
            for (int i = 0; i < encrypted.length; i++) {
                xored[i] = (byte) (encrypted[i] ^ K[i % K.length]);
            }
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(xored));
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = gis.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        } catch (Exception e) {
            return new byte[0];
        }
    }

    public static boolean checkSecurity() {
        if (nativeLoaded) {
            try {
                return c();
            } catch (Throwable ignored) {}
        }
        return true;
    }
}
