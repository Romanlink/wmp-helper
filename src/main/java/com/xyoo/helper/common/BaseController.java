package com.xyoo.helper.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 控制器基类 — 提供统一的「当前登录人」获取能力。
 * <p>
 * 各业务 Controller 继承本类后，可直接调用 {@link #getCurInfo()} 获取当前登录用户信息，
 * 无需自行解析 Token。登录人信息由 {@code AuthInterceptor} 在请求入口写入请求属性。
 * </p>
 * <p>
 * 注意：仅经过 {@code AuthInterceptor} 拦截的请求（即 {@code /api/**} 中未被排除的路径）
 * 才会写入登录人；登录、登出、SSE 流式对话（/api/chat/stream）、文件下载等被排除的路径上
 * {@link #getCurInfo()} 将返回 {@code null}。
 * </p>
 */
public abstract class BaseController {

    /**
     * 获取当前登录用户信息。
     *
     * @return 当前登录人；未登录、非 Web 请求或上下文中无信息时返回 {@code null}
     */
    protected LoginUser getCurInfo() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        Object value = request.getAttribute(LoginUser.REQUEST_ATTR);
        if (value instanceof LoginUser) {
            return (LoginUser) value;
        }
        return null;
    }

    /**
     * 获取当前登录用户ID（便捷方法）。
     *
     * @return 用户ID；未登录时返回 {@code null}
     */
    protected Long getCurUserId() {
        LoginUser user = getCurInfo();
        return user == null ? null : user.getUserId();
    }
}
