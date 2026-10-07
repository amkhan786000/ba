package com.rahbar.dto;

public class AuthResponse {
    private boolean otpRequired;
    private String token;
    /** users.id: identifies the user in every later request. */
    private Long id;
    /** The user's code (users.user_id), for display only. */
    private String userId;
    private String name;
    private Integer roleId;
    private String status;
    private String message;
    /** True when the user must choose a new password before using the app. */
    private boolean mustChangePassword;

    public static AuthResponse otpRequired(Long id, String message) {
        AuthResponse r = new AuthResponse();
        r.otpRequired = true;
        r.id = id;
        r.message = message;
        return r;
    }

    public static AuthResponse success(String token, Long id, String userId, String name, Integer roleId, String status) {
        AuthResponse r = new AuthResponse();
        r.token = token;
        r.id = id;
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
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
