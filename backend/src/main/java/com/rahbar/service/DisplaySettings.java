package com.rahbar.service;

import com.rahbar.entity.AppSetting;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.AppSettingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Administration > Application Settings: how the app shows things to everyone. For now the date format, used by
 * every screen (admin area and portals); DD/MM/YYYY unless changed.
 */
@Service
public class DisplaySettings {

    static final String DATE_FORMAT = "display.date_format";
    public static final String DEFAULT_DATE_FORMAT = "DD/MM/YYYY";

    /** Format code -> example, in the order offered. */
    public static final Map<String, String> DATE_FORMATS = new LinkedHashMap<>();
    static {
        DATE_FORMATS.put("DD/MM/YYYY", "31/12/2026");
        DATE_FORMATS.put("MM/DD/YYYY", "12/31/2026");
        DATE_FORMATS.put("YYYY-MM-DD", "2026-12-31");
        DATE_FORMATS.put("D MMM YYYY", "31 Dec 2026");
    }

    private final AppSettingRepository settings;

    public DisplaySettings(AppSettingRepository settings) {
        this.settings = settings;
    }

    public String dateFormat() {
        return settings.findById(DATE_FORMAT).map(AppSetting::getValue).filter(DATE_FORMATS::containsKey).orElse(DEFAULT_DATE_FORMAT);
    }

    public Map<String, Object> view() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date_format", dateFormat());
        body.put("date_formats", DATE_FORMATS);
        return body;
    }

    /** Body: { dateFormat }. */
    @Transactional
    public void save(Map<String, Object> body) {
        if (!body.containsKey("dateFormat")) return;
        String f = body.get("dateFormat") == null ? "" : String.valueOf(body.get("dateFormat")).trim();
        if (!DATE_FORMATS.containsKey(f)) throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown date format.");
        AppSetting s = settings.findById(DATE_FORMAT).orElseGet(() -> {
            AppSetting n = new AppSetting();
            n.setKey(DATE_FORMAT);
            return n;
        });
        s.setValue(f);
        s.setUpdatedAt(LocalDateTime.now());
        settings.save(s);
    }
}
