package com.rahbar.dto;

public class OtpVerifyRequest {
    private String userId;
    private String otp;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
}
