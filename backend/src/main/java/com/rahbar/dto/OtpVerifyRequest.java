package com.rahbar.dto;

public class OtpVerifyRequest {
    /** users.id of the user signing in (returned by the login step). */
    private Long id;
    private String otp;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
}
