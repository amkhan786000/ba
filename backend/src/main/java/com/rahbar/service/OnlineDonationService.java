package com.rahbar.service;

import com.rahbar.entity.OnlinePayment;
import com.rahbar.entity.WebProject;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.OnlinePaymentRepository;
import com.rahbar.repository.WebProjectRepository;
import com.rahbar.util.RazorpayGateway;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Donations from biharanjuman.com (Donate page): Indian citizens paying from their own Indian bank account (no FCRA),
 * by UPI, Indian card or net banking through Razorpay. PAN and address are required for the 80G receipt and Form 10BD.
 * Paying, confirming and receipts work as for sponsors (OnlinePaymentService).
 */
@Service
public class OnlineDonationService {

    static final BigDecimal MIN = new BigDecimal("100");
    static final BigDecimal MAX = new BigDecimal("1000000");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final OnlinePaymentRepository payments;
    private final WebProjectRepository projects;
    private final OnlinePaymentService online;
    private final RazorpayGateway gateway;

    public OnlineDonationService(OnlinePaymentRepository payments, WebProjectRepository projects, OnlinePaymentService online, RazorpayGateway gateway) {
        this.payments = payments;
        this.projects = projects;
        this.online = online;
        this.gateway = gateway;
    }

    /** What the Donate page needs: whether donations are open, limits and the projects to give to. */
    @Transactional(readOnly = true)
    public Map<String, Object> config() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("available", online.available());
        body.put("test_mode", gateway.testMode());
        body.put("min_amount", MIN);
        body.put("max_amount", MAX);
        List<Map<String, Object>> list = new ArrayList<>();
        for (WebProject p : projects.findByActiveTrueOrderByDisplayOrderAscNameAsc()) list.add(Map.of("id", p.getId(), "name", p.getName()));
        body.put("projects", list);
        return body;
    }

    /** Body: { name, email, phone, pan, address, amount, projectId, declaration }. Returns what the payment window needs. */
    @Transactional
    public Map<String, Object> order(Map<String, Object> body) {
        if (!online.available()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Online donations are not open yet. Please contact us to donate.");
        if (!Boolean.parseBoolean(String.valueOf(body.get("declaration")))) {
            throw bad("Please confirm that you are an Indian citizen donating from your own Indian bank account.");
        }
        String name = text(body.get("name"), "Your name", 150, true);
        if (name.length() < 2) throw bad("Please enter your full name.");
        String email = text(body.get("email"), "Email", 150, true);
        if (!EMAIL.matcher(email).matches()) throw bad("Please check your email address: the receipt is sent there.");
        String phone = text(body.get("phone"), "Phone", 30, false);
        String pan = OnlinePaymentService.pan(body.get("pan"));
        String address = text(body.get("address"), "Address", 500, true);
        BigDecimal amount;
        try {
            amount = new BigDecimal(String.valueOf(body.get("amount")).trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            throw bad("Please enter the amount in rupees.");
        }
        if (amount.compareTo(MIN) < 0) throw bad("The smallest online donation is Rs. " + MIN + ".");
        if (amount.compareTo(MAX) > 0) throw bad("For more than Rs. 10,00,000 please contact us for bank transfer details.");
        String note = "General donation";
        Long projectId = null;
        Object pid = body.get("projectId");
        if (pid != null && !String.valueOf(pid).isBlank()) {
            WebProject proj = projects.findById(Long.valueOf(String.valueOf(pid).trim())).filter(WebProject::isActive)
                    .orElseThrow(() -> bad("Unknown project."));
            projectId = proj.getId();
            note = "Project: " + proj.getName();
        }
        OnlinePayment p = new OnlinePayment();
        p.setPurpose(OnlinePayment.DONATION);
        p.setAmount(amount);
        p.setDonorName(name);
        p.setDonorEmail(email);
        p.setDonorPhone(phone);
        p.setDonorPan(pan);
        p.setDonorAddress(address);
        p.setNote(note);
        p.setDonationProjectId(projectId);
        p.setCitizenDeclared(true);
        payments.save(p);
        p.setGatewayOrderId(gatewayOrder(p, projectId));
        payments.save(p);
        return online.checkout(p, note);
    }

    private String gatewayOrder(OnlinePayment p, Long projectId) {
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("online_payment_id", String.valueOf(p.getId()));
        notes.put("purpose", "DONATION");
        if (projectId != null) notes.put("project_id", String.valueOf(projectId));
        return gateway.createOrder(p.getAmount(), "rahbar-don-" + p.getId(), notes);
    }

    private static String text(Object v, String label, int max, boolean required) {
        String s = v == null ? "" : String.valueOf(v).strip();
        if (s.isEmpty()) {
            if (required) throw bad(label + " is required.");
            return null;
        }
        if (s.length() > max) throw bad(label + " is too long.");
        return s;
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
