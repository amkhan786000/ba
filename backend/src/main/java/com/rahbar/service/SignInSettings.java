package com.rahbar.service;

import com.rahbar.entity.AppSetting;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.AppSettingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/** Administration > Application Settings > Sign-in: how long a remembered device may skip the emailed code. */
@Service
public class SignInSettings {

    static final String REMEMBER_DAYS = "security.remember_device_days";
    public static final int DEFAULT_REMEMBER_DAYS = 30;
    /** Longest allowed; 0 switches "Remember this device" off (and stops existing remembered devices skipping the code). */
    public static final int MAX_REMEMBER_DAYS = 90;

    private final AppSettingRepository settings;

    public SignInSettings(AppSettingRepository settings) {
        this.settings = settings;
    }

    public int rememberDays() {
        return settings.findById(REMEMBER_DAYS).map(AppSetting::getValue).map(SignInSettings::parse)
                .filter(d -> d >= 0 && d <= MAX_REMEMBER_DAYS).orElse(DEFAULT_REMEMBER_DAYS);
    }

    /** Body: { rememberDeviceDays: 0..90 } (ignored when absent). */
    @Transactional
    public void save(Map<String, Object> body) {
        if (!body.containsKey("rememberDeviceDays")) return;
        Integer days = body.get("rememberDeviceDays") == null ? null : parse(String.valueOf(body.get("rememberDeviceDays")));
        if (days == null || days < 0 || days > MAX_REMEMBER_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Remember a device for 0 to " + MAX_REMEMBER_DAYS + " days (0 turns it off).");
        }
        AppSetting s = settings.findById(REMEMBER_DAYS).orElseGet(() -> {
            AppSetting n = new AppSetting();
            n.setKey(REMEMBER_DAYS);
            return n;
        });
        s.setValue(String.valueOf(days));
        s.setUpdatedAt(LocalDateTime.now());
        settings.save(s);
    }

    private static Integer parse(String v) {
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
