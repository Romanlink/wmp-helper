package com.xyoo.helper.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 全局 CORS 过滤器（最高优先级）
 *
 * <p>关键点：把响应的 {@code Access-Control-Allow-Origin} 设为浏览器实际请求的
 * {@code Origin}（而不是通配符 {@code *}）。浏览器规定：当请求携带凭证时，
 * 服务端不能返回 {@code *}，必须返回具体来源，否则直接报 CORS 错误。反射真实
 * Origin 可同时兼容「带凭证」与「不带凭证」两种场景，彻底规避 {@code *} + 凭证冲突。
 */
@Configuration
public class CorsConfig {

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> corsFilter() {
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                String origin = request.getHeader("Origin");
                if (origin != null && !origin.isEmpty()) {
                    // 反射真实来源（绝不用 *），并允许凭证
                    response.setHeader("Access-Control-Allow-Origin", origin);
                    response.setHeader("Access-Control-Allow-Credentials", "true");
                    // 允许的方法：优先回显预检请求声明的 Method
                    String reqMethod = request.getHeader("Access-Control-Request-Method");
                    response.setHeader("Access-Control-Allow-Methods",
                            reqMethod != null ? reqMethod : "GET, POST, PUT, DELETE, OPTIONS");
                    // 允许的请求头：回显预检声明的 Headers（如 Authorization）
                    String reqHeaders = request.getHeader("Access-Control-Request-Headers");
                    response.setHeader("Access-Control-Allow-Headers",
                            reqHeaders != null ? reqHeaders : "*");
                    response.setHeader("Access-Control-Max-Age", "3600");
                }

                // 预检请求直接放行，避免进入业务/拦截器
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    return;
                }
                chain.doFilter(request, response);
            }
        };

        FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/api/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);   // 先于拦截器与业务执行
        return reg;
    }
}
