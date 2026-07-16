package com.xyoo.helper.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FileEncryptionUtil 单元测试：AES-256-GCM 加解密往返、错误密码、随机密码生成。
 */
class FileEncryptionUtilTest {

    @Test
    @DisplayName("加密后解密得到原文（含中文）")
    void roundTrip() {
        byte[] plain = "敏感内容 hello world 中文".getBytes(StandardCharsets.UTF_8);
        String pwd = "my-secret-password";

        byte[] encrypted = FileEncryptionUtil.encrypt(plain, pwd);
        assertThat(encrypted).isNotEqualTo(plain);

        byte[] decrypted = FileEncryptionUtil.decrypt(encrypted, pwd);
        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    @DisplayName("空数据可正常加解密")
    void emptyRoundTrip() {
        byte[] encrypted = FileEncryptionUtil.encrypt(new byte[0], "pwd");
        assertThat(FileEncryptionUtil.decrypt(encrypted, "pwd")).isEmpty();
    }

    @Test
    @DisplayName("错误密码解密抛 RuntimeException")
    void wrongPassword() {
        byte[] encrypted = FileEncryptionUtil.encrypt("data".getBytes(StandardCharsets.UTF_8), "right");
        assertThatThrownBy(() -> FileEncryptionUtil.decrypt(encrypted, "wrong"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("generatePassword 非空且每次不同")
    void generatePassword() {
        String p1 = FileEncryptionUtil.generatePassword();
        String p2 = FileEncryptionUtil.generatePassword();
        assertThat(p1).isNotBlank();
        assertThat(p2).isNotBlank();
        assertThat(p1).isNotEqualTo(p2);
    }
}
