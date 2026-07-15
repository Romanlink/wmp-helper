package com.xyoo.helper.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置 — 认证拦截器
 * （跨域由 CorsConfig 中的过滤器统一处理，反射真实 Origin，规避 `*` + 凭证冲突）
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",           // 登录接口
                        "/api/auth/logout",           // 登出接口
                        "/api/chat/stream",           // SSE 流式对话（EventSource 无法设置 Header）
                        "/api/docs/*/download"        // 文件下载（window.open 无法设置 Header）
                );
    }
}
