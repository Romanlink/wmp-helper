package com.xyoo.helper.common;

/**
 * 当前登录用户上下文。
 * <p>
 * 由 {@code AuthInterceptor} 在请求入口（preHandle 校验 Token 通过后）写入
 * {@link jakarta.servlet.http.HttpServletRequest} 的属性中；
 * 业务 Controller 继承 {@link BaseController} 后可通过 {@link BaseController#getCurInfo()} 读取。
 * </p>
 */
public class LoginUser {

    /** request 属性名，用于存放当前登录人 */
    public static final String REQUEST_ATTR = "CURRENT_LOGIN_USER";

    private Long userId;
    private String username;
    private String realName;
    private Long roleId;
    private String roleName;
    private String roleCode;

    public LoginUser() {
    }

    public LoginUser(Long userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }
}
