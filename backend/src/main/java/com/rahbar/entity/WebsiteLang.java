package com.rahbar.entity;

import com.rahbar.entity.WebsiteLang;

import com.rahbar.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Locale;

/** Website languages. English is the main language; Urdu and Hindi fall back to it. */
public enum WebsiteLang {
    EN, UR, HI;

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Unknown or missing codes mean English. */
    public static WebsiteLang of(String code) {
        if (code == null || code.isBlank()) return EN;
        String c = code.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(l -> l.code().equals(c) || c.startsWith(l.code() + "-")).findFirst().orElse(EN);
    }

    public static WebsiteLang require(String code) {
        WebsiteLang l = of(code);
        if (!l.code().equalsIgnoreCase(code == null ? "" : code.strip())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown language " + code + " (use en, ur or hi)");
        }
        return l;
    }
}
