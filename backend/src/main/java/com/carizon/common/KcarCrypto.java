package com.carizon.common;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class KcarCrypto {
    private static final String KEY = "SKFJ2424DasfaJRI";   // 16바이트 (AES-128)
    private static final String IV  = "sfq241sf3dscs321";   // 16바이트

    public static String encrypt(String plainJson) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding"); // PKCS7 ≒ PKCS5Padding
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] enc = cipher.doFinal(plainJson.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(enc);
    }
}
