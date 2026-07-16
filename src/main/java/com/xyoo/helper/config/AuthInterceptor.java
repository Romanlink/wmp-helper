package com.xyoo.helper.config;

import com.xyoo.helper.service.AuthService;
import com.xyoo.helper.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证拦截器 — 校验请求中的 JWT Token
 * <p>
 * 逻辑：
 * <ol>
 *   <li>从 Authorization 请求头提取 Bearer token</li>
 *   <li>JWT 格式校验 + Redis 中 token 是否匹配（单点登录检测）</li>
 *   <li>如果 Redis 中 token 已被覆盖 → 返回 401 + "您的账号已在其他设备登录，请注意密码保护"</li>
 *   <li>如果 token 无效或缺失 → 返回 401 + "未登录或登录已过期"</li>
 * </ol>
 * </p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private final AuthService authService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // OPTIONS 预检请求直接放行
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // 提取 Token
        String token = extractToken(request);

        // Token 为空
        if (token == null || token.isEmpty()) {
            sendError(response, 401, "未登录或登录已过期");
            return false;
        }

        // JWT 格式校验
        if (!JwtUtil.isValid(token)) {
            sendError(response, 401, "登录已过期，请重新登录");
            return false;
        }

        // Redis 中 token 校验（单点登录：如果被覆盖则提示）
        boolean valid = authService.isTokenValid(token);
        if (!valid) {
            // 进一步判断：是 token 过期还是被其他设备登录替换
            Long userId = JwtUtil.getUserId(token);
            if (userId != null) {
                // 能解析出 userId 但 Redis 中 token 不匹配 → 被其他设备登录替换
                sendError(response, 401, "您的账号已在其他设备登录，请注意密码保护");
            } else {
                sendError(response, 401, "登录已过期，请重新登录");
            }
            return false;
        }

        return true;
    }

    /**
     * 从请求头提取 Bearer token
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    /**
     * 返回 401 JSON 响应
     */
    private void sendError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
