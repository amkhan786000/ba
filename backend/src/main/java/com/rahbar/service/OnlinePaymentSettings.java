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
import java.util.regex.Pattern;

/**
 * Online payment settings kept in Rahbar (Sponsors & Payments > Online Payments): whether online payments are switched
 * on, and the details printed on 80G receipts. The Razorpay keys are server settings, not kept here.
 */
@Service
public class OnlinePaymentSettings {

    static final Pattern PAN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");

    /** Setting key -> label, in form order. */
    public static final Map<String, String> RECEIPT_FIELDS = new LinkedHashMap<>();
    static {
        RECEIPT_FIELDS.put("receipt.legal_name", "Legal name (as registered)");
        RECEIPT_FIELDS.put("receipt.address", "Registered address");
        RECEIPT_FIELDS.put("receipt.pan", "PAN of the organisation");
        RECEIPT_FIELDS.put("receipt.80g_number", "80G registration number");
        RECEIPT_FIELDS.put("receipt.80g_valid", "80G valid (e.g. AY 2022-23 to AY 2026-27)");
        RECEIPT_FIELDS.put("receipt.12a_number", "12A registration number");
        RECEIPT_FIELDS.put("receipt.signatory", "Signed by (name and title)");
    }
    private static final String ENABLED = "online.enabled";

    private final AppSettingRepository settings;

    public OnlinePaymentSettings(AppSettingRepository settings) {
        this.settings = settings;
    }

    public boolean enabled() {
        return "true".equals(get(ENABLED));
    }

    public String get(String key) {
        return settings.findById(key).map(AppSetting::getValue).orElse("");
    }

    /** The receipt details (empty strings when not set). */
    public Map<String, String> receipt() {
        Map<String, String> out = new LinkedHashMap<>();
        RECEIPT_FIELDS.keySet().forEach(k -> out.put(k, get(k)));
        return out;
    }

    /** True when the details a valid 80G receipt needs are filled in. */
    public boolean receiptReady() {
        Map<String, String> r = receipt();
        return !r.get("receipt.legal_name").isBlank() && !r.get("receipt.address").isBlank()
                && !r.get("receipt.pan").isBlank() && !r.get("receipt.80g_number").isBlank();
    }

    /** Body: { enabled, "receipt.legal_name": ..., ... }. Online payments can only be switched on with the receipt details. */
    @Transactional
    public void save(Map<String, Object> body) {
        for (String key : RECEIPT_FIELDS.keySet()) {
            if (!body.containsKey(key)) continue;
            String v = body.get(key) == null ? "" : String.valueOf(body.get(key)).strip();
            if (v.length() > 500) throw bad(RECEIPT_FIELDS.get(key) + " is too long.");
            if (key.equals("receipt.pan") && !v.isEmpty()) {
                v = v.toUpperCase();
                if (!PAN.matcher(v).matches()) throw bad("The organisation's PAN looks wrong (e.g. AAATB1234C).");
            }
            put(key, v);
        }
        if (body.containsKey("enabled")) {
            boolean on = Boolean.parseBoolean(String.valueOf(body.get("enabled")));
            if (on && !receiptReady()) throw bad("Fill in the legal name, address, PAN and 80G number first: every online payment gets an 80G receipt.");
            put(ENABLED, String.valueOf(on));
        }
    }

    private void put(String key, String value) {
        AppSetting s = settings.findById(key).orElseGet(() -> {
            AppSetting n = new AppSetting();
            n.setKey(key);
            return n;
        });
        s.setValue(value);
        s.setUpdatedAt(LocalDateTime.now());
        settings.save(s);
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
