package com.rahbar.dto;

public class OtpVerifyRequest {
    /** users.id of the user signing in (returned by the login step). */
    private Long id;
    private String otp;
    /** "Remember this device": skip the code on this browser next time (until it expires). */
    private boolean rememberDevice;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
    public boolean isRememberDevice() { return rememberDevice; }
    public void setRememberDevice(boolean rememberDevice) { this.rememberDevice = rememberDevice; }
}
