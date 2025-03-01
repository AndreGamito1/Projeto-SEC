package com.example;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.util.Base64;

public class AESUtils {

    // Encrypt Message using AES
    public static String encrypt(String data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedData = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedData);
    }

    // Decrypt Message using AES
    public static String decrypt(String encryptedData, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decodedData = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
        return new String(decodedData);
    }
}
