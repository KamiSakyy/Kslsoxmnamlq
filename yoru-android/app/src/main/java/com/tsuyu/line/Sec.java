package com.tsuyu.line;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

public final class Sec {
    private static final byte[] K = initK();

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
        try {
            int len = hex.length();
            byte[] out = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                        + Character.digit(hex.charAt(i + 1), 16));
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
}
