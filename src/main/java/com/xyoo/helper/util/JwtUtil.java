package com.xyoo.helper.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT 工具类 — 生成和解析 Token
 * <p>
 * 使用 HS256 算法，密钥固定（适合单体应用场景）。
 * </p>
 */
public class JwtUtil {

    /** 密钥（至少 256 bit） */
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(
            "Helper2026SecretKeyForJwtTokenGeneration!!".getBytes()
    );

    /** Token 有效期：24 小时 */
    private static final long EXPIRATION_MS = 24 * 60 * 60 * 1000L;

    /**
     * 生成 JWT Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @return JWT token 字符串
     */
    public static String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + EXPIRATION_MS);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 JWT Token，返回 Claims
     *
     * @param token JWT token
     * @return Claims，解析失败返回 null
     */
    public static Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(SECRET_KEY)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 Token 中提取用户ID
     */
    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return null;
        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从 Token 中提取用户名
     */
    public static String getUsername(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return null;
        return claims.get("username", String.class);
    }

    /**
     * 验证 Token 是否有效（未过期且可解析）
     */
    public static boolean isValid(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return false;
        return claims.getExpiration().after(new Date());
    }
}
