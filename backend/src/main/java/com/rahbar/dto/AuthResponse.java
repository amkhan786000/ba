package com.rahbar.dto;

public class AuthResponse {
    private boolean otpRequired;
    private String token;
    private String userId;
    private String name;
    private Integer roleId;
    private String status;
    private String message;
    /** True when the user must choose a new password before using the app. */
    private boolean mustChangePassword;

    public static AuthResponse otpRequired(String userId, String message) {
        AuthResponse r = new AuthResponse();
        r.otpRequired = true;
        r.userId = userId;
        r.message = message;
        return r;
    }

    public static AuthResponse success(String token, String userId, String name, Integer roleId, String status) {
        AuthResponse r = new AuthResponse();
        r.token = token;
        r.userId = userId;
        r.name = name;
        r.roleId = roleId;
        r.status = status;
        return r;
    }

    public boolean isOtpRequired() { return otpRequired; }
    public void setOtpRequired(boolean otpRequired) { this.otpRequired = otpRequired; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getRoleId() { return roleId; }
    public void setRoleId(Integer roleId) { this.roleId = roleId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
