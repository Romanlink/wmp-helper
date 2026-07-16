package com.xyoo.helper.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtUtil 单元测试：Token 生成/解析往返、非法/篡改 Token 校验。
 */
class JwtUtilTest {

    @Test
    @DisplayName("生成 token 后可解析出 userId 与 username，且有效")
    void roundTrip() {
        String token = JwtUtil.generateToken(42L, "bob");
        assertThat(JwtUtil.getUserId(token)).isEqualTo(42L);
        assertThat(JwtUtil.getUsername(token)).isEqualTo("bob");
        assertThat(JwtUtil.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("isValid 对 null / 篡改 token 返回 false")
    void invalid() {
        assertThat(JwtUtil.isValid(null)).isFalse();
        assertThat(JwtUtil.isValid("not.a.jwt")).isFalse();

        String token = JwtUtil.generateToken(1L, "x");
        String tampered = token.substring(0, token.length() - 2) + "aa";
        assertThat(JwtUtil.isValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("parseToken 非法 token 返回 null")
    void parseNull() {
        assertThat(JwtUtil.parseToken("garbage")).isNull();
    }
}
