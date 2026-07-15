package com.xyoo.helper.util;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 文件加解密工具类
 * <p>
 * 使用 AES-256-GCM 对上传的 PDF 文件进行加密存储，
 * 下载时自动解密后返回，防止直接通过文件系统访问原始内容。
 * </p>
 */
public final class FileEncryptionUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;  // 96 bits (recommended for GCM)
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int AES_KEY_LENGTH = 32;  // 256 bits

    private FileEncryptionUtil() {
    }

    /**
     * 生成随机密码（16 位字母数字混合）
     */
    public static String generatePassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 加密字节数组
     *
     * @param plainData 原始数据
     * @param password  密码
     * @return 加密后的数据（IV + 密文，IV 在前 12 字节）
     */
    public static byte[] encrypt(byte[] plainData, String password) {
        try {
            SecretKeySpec key = deriveKey(password);

            // 生成随机 IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            byte[] encrypted = cipher.doFinal(plainData);

            // 将 IV 拼接在密文前面
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);

            return result;
        } catch (Exception e) {
            throw new RuntimeException("文件加密失败", e);
        }
    }

    /**
     * 解密字节数组
     *
     * @param encryptedData 加密数据（IV + 密文）
     * @param password      密码
     * @return 解密后的原始数据
     */
    public static byte[] decrypt(byte[] encryptedData, String password) {
        try {
            // 提取 IV（前 12 字节）
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[encryptedData.length - GCM_IV_LENGTH];
            System.arraycopy(encryptedData, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedData, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            SecretKeySpec key = deriveKey(password);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            throw new RuntimeException("文件解密失败，密码可能不正确", e);
        }
    }

    /**
     * 从密码派生 AES-256 密钥（SHA-256）
     */
    private static SecretKeySpec deriveKey(String password) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = sha.digest(password.getBytes("UTF-8"));
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
}
