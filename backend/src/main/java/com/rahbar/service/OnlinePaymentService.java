package com.rahbar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rahbar.dto.EmailAttachment;
import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.security.Access;
import com.rahbar.security.Section;
import com.rahbar.util.RazorpayGateway;
import com.rahbar.util.UserRoles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Online payments through Razorpay (Indian payments only: Bihar Anjuman has no FCRA registration). Sponsors who pay
 * through Rahbar pay their students' installments from the Sponsor portal; the office then passes the money on to each
 * student. Every paid online payment gets an 80G receipt by email. A payment counts once Razorpay's signature checks
 * out, whether the browser or Razorpay's webhook reports it first.
 */
@Service
public class OnlinePaymentService {

    private static final Logger log = LoggerFactory.getLogger(OnlinePaymentService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final OnlinePaymentRepository payments;
    private final OnlinePaymentItemRepository items;
    private final PaymentInstallmentRepository installments;
    private final PaymentRepository paymentRecords;
    private final UserRepository users;
    private final GrantorGranteeRepository mappings;
    private final RoleRepository roles;
    private final PaymentInstallmentService installmentService;
    private final RazorpayGateway gateway;
    private final OnlinePaymentSettings settings;
    private final OnlinePaymentReceipts receipts;
    private final EmailService email;
    private final NotificationService notifications;
    private final ObjectMapper json = new ObjectMapper();
    /** Books paid website donations in Income & Expenses. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DonationBooks books;

    public OnlinePaymentService(OnlinePaymentRepository payments, OnlinePaymentItemRepository items, PaymentInstallmentRepository installments,
                                PaymentRepository paymentRecords, UserRepository users, GrantorGranteeRepository mappings, RoleRepository roles,
                                PaymentInstallmentService installmentService, RazorpayGateway gateway, OnlinePaymentSettings settings,
                                OnlinePaymentReceipts receipts, EmailService email, NotificationService notifications) {
        this.payments = payments;
        this.items = items;
        this.installments = installments;
        this.paymentRecords = paymentRecords;
        this.users = users;
        this.mappings = mappings;
        this.roles = roles;
        this.installmentService = installmentService;
        this.gateway = gateway;
        this.settings = settings;
        this.receipts = receipts;
        this.email = email;
        this.notifications = notifications;
    }

    /** True when online payments are switched on and the Razorpay keys are set. */
    public boolean available() {
        return settings.enabled() && gateway.configured();
    }

    // ---------------------------------------------------------------- sponsor

    /** What the sponsor's Payments page needs: whether they may pay online, their PAN, and their online payments. */
    @Transactional(readOnly = true)
    public Map<String, Object> sponsorInfo(Long sponsorId) {
        User s = users.findById(sponsorId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sponsor not found."));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pays_through_rahbar", s.isPaysThroughRahbar());
        body.put("available", available());
        body.put("test_mode", gateway.testMode());
        body.put("pan", s.getPan());
        List<Map<String, Object>> list = new ArrayList<>();
        for (OnlinePayment p : payments.findBySponsorUserIdAndStatusOrderByPaidAtDesc(sponsorId, OnlinePayment.PAID)) {
            list.add(Map.of("id", p.getId(), "receipt_no", p.getReceiptNo(), "amount", p.getAmount(), "paid_at", p.getPaidAt()));
        }
        body.put("payments", list);
        return body;
    }

    /**
     * Body: { installmentIds: [...], pan, declaration: true }. Opens an order for the chosen unpaid installments of the
     * sponsor's current students; returns what the payment window needs.
     */
    @Transactional
    public Map<String, Object> sponsorOrder(Long sponsorId, Map<String, Object> body) {
        User s = users.findById(sponsorId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sponsor not found."));
        if (!s.isPaysThroughRahbar()) throw bad("Your payments go directly to your students. Ask the office if you would like to pay through Rahbar.");
        if (!available()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Online payments are not open yet.");
        if (!Boolean.parseBoolean(String.valueOf(body.get("declaration")))) {
            throw bad("Please confirm that you are an Indian citizen paying from your own Indian bank account.");
        }
        String pan = pan(body.get("pan"));
        s.setPan(pan);
        users.save(s);
        if (!(body.get("installmentIds") instanceof List<?> ids) || ids.isEmpty()) throw bad("Choose at least one installment.");
        Set<Long> current = new HashSet<>();
        mappings.findByGrantorId(sponsorId).forEach(m -> current.add(m.getGranteeId()));
        List<PaymentInstallment> chosen = new ArrayList<>();
        for (Object o : new LinkedHashSet<>(ids)) {
            Long id = Long.valueOf(String.valueOf(o));
            PaymentInstallment i = installments.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Installment not found."));
            if (!sponsorId.equals(i.getGrantorId()) || !current.contains(i.getGranteeId())) throw bad("That installment is not for one of your current students.");
            if (i.getPaymentId() != null) throw bad("Installment #" + i.getInstallmentNo() + " is already paid.");
            installmentService.requireStudying(i.getGranteeId());
            chosen.add(i);
        }
        BigDecimal total = chosen.stream().map(PaymentInstallment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) throw bad("Nothing to pay.");

        OnlinePayment p = new OnlinePayment();
        p.setPurpose(OnlinePayment.SPONSOR);
        p.setAmount(total);
        p.setSponsorUserId(sponsorId);
        p.setDonorName(s.getName());
        p.setDonorEmail(s.getEmail());
        p.setDonorPhone(s.getPhone());
        p.setDonorPan(pan);
        p.setCitizenDeclared(true);
        payments.save(p);
        for (PaymentInstallment i : chosen) {
            OnlinePaymentItem it = new OnlinePaymentItem();
            it.setOnlinePaymentId(p.getId());
            it.setInstallmentId(i.getInstallmentId());
            it.setStudentUserId(i.getGranteeId());
            it.setAmount(i.getAmount());
            items.save(it);
        }
        p.setGatewayOrderId(gateway.createOrder(total, "rahbar-op-" + p.getId(),
                Map.of("online_payment_id", String.valueOf(p.getId()), "purpose", "SPONSOR", "sponsor", s.getUserId())));
        payments.save(p);
        return checkout(p, "Scholarship installments (" + chosen.size() + ")");
    }

    /** What the Razorpay payment window needs (the key id is public; the secret never leaves the server). */
    Map<String, Object> checkout(OnlinePayment p, String description) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("key", gateway.keyId());
        out.put("order_id", p.getGatewayOrderId());
        out.put("amount", p.getAmount().movePointRight(2).longValue());
        out.put("currency", "INR");
        out.put("name", settings.get("receipt.legal_name").isBlank() ? "Bihar Anjuman" : settings.get("receipt.legal_name"));
        out.put("description", description);
        Map<String, String> prefill = new LinkedHashMap<>();
        if (p.getDonorName() != null) prefill.put("name", p.getDonorName());
        if (usableEmail(p.getDonorEmail())) prefill.put("email", p.getDonorEmail());
        if (p.getDonorPhone() != null) prefill.put("contact", p.getDonorPhone());
        out.put("prefill", prefill);
        out.put("test_mode", gateway.testMode());
        return out;
    }

    /** The payment window finished: check Razorpay's signature, then record the payment. */
    @Transactional
    public OnlinePayment confirm(String orderId, String paymentId, String signature) {
        if (!gateway.paymentSignatureValid(orderId, paymentId, signature)) {
            throw bad("We could not confirm this payment. If money left your account, it will be matched automatically; otherwise contact the office.");
        }
        return markPaid(orderId, paymentId);
    }

    /** Razorpay's webhook (payment.captured / order.paid): the backup when the browser closed before confirming. */
    @Transactional
    public void webhook(String rawBody, String signature) {
        if (!gateway.webhookSignatureValid(rawBody, signature)) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid signature.");
        try {
            JsonNode root = json.readTree(rawBody);
            String event = root.path("event").asText();
            if (!event.equals("payment.captured") && !event.equals("order.paid")) return;
            JsonNode payment = root.path("payload").path("payment").path("entity");
            String orderId = payment.path("order_id").asText(null);
            String paymentId = payment.path("id").asText(null);
            if (orderId != null && paymentId != null) markPaid(orderId, paymentId);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Razorpay webhook could not be read: {}", e.getMessage());
        }
    }

    /** Records the payment once (the order row is locked; a second report changes nothing). */
    OnlinePayment markPaid(String orderId, String paymentId) {
        OnlinePayment p = payments.lockByGatewayOrderId(orderId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found."));
        if (OnlinePayment.PAID.equals(p.getStatus())) return p;
        p.setStatus(OnlinePayment.PAID);
        p.setGatewayPaymentId(paymentId);
        p.setPaidAt(LocalDateTime.now());
        p.setReceiptNo(OnlinePaymentReceipts.receiptNo(p.getId(), p.getPaidAt()));
        payments.save(p);
        List<String> lines = new ArrayList<>();
        if (OnlinePayment.SPONSOR.equals(p.getPurpose())) recordInstallments(p, lines);
        if (OnlinePayment.DONATION.equals(p.getPurpose())) {
            if (books != null) books.record(p, projectOf(p));
            notifyOffice("New online donation", p.getDonorName() + " donated Rs. " + money(p.getAmount()) + " online (" + p.getNote() + ").");
        }
        afterPaid(p, lines);
        return p;
    }

    /** Each installment: a Paid payment (method ONLINE) linked to it; the student is told Rahbar will send it on. */
    private void recordInstallments(OnlinePayment p, List<String> lines) {
        for (OnlinePaymentItem it : items.findByOnlinePaymentId(p.getId())) {
            PaymentInstallment i = installments.findById(it.getInstallmentId()).orElse(null);
            User student = users.findById(it.getStudentUserId()).orElse(null);
            String who = student == null ? "a student" : student.getName() + " (" + student.getUserId() + ")";
            lines.add("installment #" + (i == null ? "?" : i.getInstallmentNo()) + " for " + who);
            if (i == null || i.getPaymentId() != null) {
                // Paid some other way meanwhile: the office sorts it out (refund or next installment).
                notifyOffice("Online payment needs a look", p.getDonorName() + " paid online for an installment that was already paid ("
                        + (student == null ? "" : student.getName()) + "). Receipt " + p.getReceiptNo() + ".");
                continue;
            }
            Payment pay = new Payment();
            pay.setGrantorId(p.getSponsorUserId());
            pay.setGranteeId(it.getStudentUserId());
            pay.setAmount(it.getAmount());
            pay.setPaymentDate(p.getPaidAt());
            pay.setStatus("Paid");
            pay.setMethod("ONLINE");
            paymentRecords.saveAndFlush(pay);
            installmentService.afterPayment(pay, i.getInstallmentId());
            it.setPaymentId(pay.getPaymentId());
            items.save(it);
            if (student != null) {
                notifications.notify(student.getId(), "Your sponsor paid through Rahbar",
                        p.getDonorName() + " paid Rs. " + money(it.getAmount()) + " for your studies through Rahbar. The office will send it to your bank account.",
                        NotificationService.PAYMENT, "/student/payments", false);
            }
        }
        notifyOffice("Online payment to pass on", p.getDonorName() + " paid Rs. " + money(p.getAmount()) + " online. Pass it on to the student(s) in Online Payments.");
    }

    /** The project a donation was for (kept in the Razorpay order notes as project_id; null: general). */
    private static Long projectOf(OnlinePayment p) {
        return p.getDonationProjectId();
    }

    /** The 80G receipt by email (when the payer has an address). */
    private void afterPaid(OnlinePayment p, List<String> lines) {
        if (!usableEmail(p.getDonorEmail())) return;
        Path file = null;
        try {
            file = Files.createTempFile("rahbar-receipt-", ".pdf");
            Files.write(file, receipts.pdf(p, lines));
            Map<String, Object> v = new HashMap<>();
            v.put("name", p.getDonorName());
            v.put("amount", money(p.getAmount()));
            v.put("date", p.getPaidAt().format(DAY));
            v.put("receipt_no", p.getReceiptNo());
            v.put("details", lines.isEmpty() ? "" : "This payment covers " + String.join("; ", lines) + ".");
            email.send(EmailType.ONLINE_PAYMENT_RECEIPT, p.getDonorEmail().trim(), v,
                    List.of(new EmailAttachment(p.getReceiptNo().replace('/', '-') + ".pdf", file)));
        } catch (Exception e) {
            log.warn("Could not email receipt {}: {}", p.getReceiptNo(), e.getMessage()); // the payment stands; the receipt can be downloaded
        } finally {
            if (file != null) try { Files.deleteIfExists(file); } catch (Exception ignored) { }
        }
    }

    /** The 80G receipt PDF: for the sponsor who paid, or for staff with ONLINE_PAYMENTS:VIEW. */
    @Transactional(readOnly = true)
    public byte[] receipt(Long onlinePaymentId, Long sponsorId) {
        OnlinePayment p = payments.findById(onlinePaymentId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Receipt not found."));
        if (sponsorId != null && !sponsorId.equals(p.getSponsorUserId())) throw new ApiException(HttpStatus.NOT_FOUND, "Receipt not found.");
        return receipts.pdf(p, describe(p));
    }

    /** The online payment an installment's payment came from (null when paid another way). */
    @Transactional(readOnly = true)
    public Long onlinePaymentOf(Long paymentId) {
        return items.findFirstByPaymentId(paymentId).map(OnlinePaymentItem::getOnlinePaymentId).orElse(null);
    }

    private List<String> describe(OnlinePayment p) {
        List<String> lines = new ArrayList<>();
        for (OnlinePaymentItem it : items.findByOnlinePaymentId(p.getId())) {
            String no = installments.findById(it.getInstallmentId()).map(i -> String.valueOf(i.getInstallmentNo())).orElse("?");
            String who = users.findById(it.getStudentUserId()).map(u -> u.getName() + " (" + u.getUserId() + ")").orElse("a student");
            lines.add("installment #" + no + " for " + who);
        }
        return lines;
    }

    // ---------------------------------------------------------------- office

    /** Online payments (newest first) with their installments and what still has to be passed on to students. */
    @Transactional(readOnly = true)
    public Map<String, Object> list(String purpose) {
        List<OnlinePayment> all = payments.findAllByOrderByIdDesc().stream()
                .filter(p -> !OnlinePayment.CREATED.equals(p.getStatus()) || p.getCreatedAt() == null || p.getCreatedAt().isAfter(LocalDateTime.now().minusDays(2)))
                .filter(p -> purpose == null || purpose.isBlank() || purpose.equals(p.getPurpose())).toList();
        Map<Long, List<OnlinePaymentItem>> byPayment = new HashMap<>();
        items.findByOnlinePaymentIdIn(all.stream().map(OnlinePayment::getId).toList())
                .forEach(i -> byPayment.computeIfAbsent(i.getOnlinePaymentId(), k -> new ArrayList<>()).add(i));
        Map<Long, User> people = new HashMap<>();
        users.findAllById(byPayment.values().stream().flatMap(List::stream).map(OnlinePaymentItem::getStudentUserId).distinct().toList())
                .forEach(u -> people.put(u.getId(), u));
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal toPassOn = BigDecimal.ZERO;
        for (OnlinePayment p : all) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", p.getId());
            r.put("purpose", p.getPurpose());
            r.put("status", p.getStatus());
            r.put("amount", p.getAmount());
            r.put("name", p.getDonorName());
            r.put("email", p.getDonorEmail());
            r.put("phone", p.getDonorPhone());
            r.put("pan", p.getDonorPan());
            r.put("note", p.getNote());
            r.put("receipt_no", p.getReceiptNo());
            r.put("gateway_payment_id", p.getGatewayPaymentId());
            r.put("paid_at", p.getPaidAt());
            r.put("created_at", p.getCreatedAt());
            List<Map<String, Object>> its = new ArrayList<>();
            for (OnlinePaymentItem it : byPayment.getOrDefault(p.getId(), List.of())) {
                User st = people.get(it.getStudentUserId());
                Map<String, Object> ir = new LinkedHashMap<>();
                ir.put("id", it.getId());
                ir.put("student_name", st == null ? null : st.getName());
                ir.put("student_ref_id", st == null ? null : st.getUserId());
                ir.put("amount", it.getAmount());
                ir.put("passed_on_at", it.getPassedOnAt());
                ir.put("passed_on_ref", it.getPassedOnRef());
                its.add(ir);
                if (OnlinePayment.PAID.equals(p.getStatus()) && it.getPaymentId() != null && it.getPassedOnAt() == null) toPassOn = toPassOn.add(it.getAmount());
            }
            r.put("items", its);
            rows.add(r);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rows", rows);
        body.put("to_pass_on", toPassOn);
        return body;
    }

    /** Body: { date (yyyy-MM-dd, default today), reference }. The student is told the money is on its way to their bank. */
    @Transactional
    public void passOn(Long itemId, Map<String, Object> body) {
        OnlinePaymentItem it = items.findById(itemId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not found."));
        OnlinePayment p = payments.findById(it.getOnlinePaymentId()).orElseThrow();
        if (!OnlinePayment.PAID.equals(p.getStatus()) || it.getPaymentId() == null) throw bad("This installment wasn't paid online.");
        if (it.getPassedOnAt() != null) throw bad("Already passed on.");
        String ref = ServiceSupport.str(body.get("reference"));
        if (ref == null || ref.isBlank()) throw bad("Enter the bank reference (UTR) of the transfer.");
        LocalDate date;
        try {
            date = ServiceSupport.isBlank(body.get("date")) ? LocalDate.now() : LocalDate.parse(String.valueOf(body.get("date")).trim());
        } catch (Exception e) {
            throw bad("Please give the date as YYYY-MM-DD.");
        }
        it.setPassedOnAt(date);
        it.setPassedOnRef(ref.strip().length() > 100 ? ref.strip().substring(0, 100) : ref.strip());
        it.setPassedOnBy(Access.current().getUser().getId());
        items.save(it);
        // To the student: which installment, never the amount.
        users.findById(it.getStudentUserId()).ifPresent(st -> notifications.notify(st.getId(), "Money sent to your bank account",
                "Your sponsor's " + installmentService.label(it.getPaymentId()) + " was sent to your bank account on " + date.format(DAY) + ".",
                NotificationService.PAYMENT, "/student/payments", EmailType.STUDENT_PAID_THROUGH_RAHBAR,
                ServiceSupport.vars("installment", installmentService.number(it.getPaymentId()), "date", date.format(DAY),
                        "reference", it.getPassedOnRef())));
    }

    /** Sponsors who pay through Rahbar, and active sponsors to choose from. */
    @Transactional(readOnly = true)
    public Map<String, Object> sponsors() {
        List<Map<String, Object>> through = new ArrayList<>(), others = new ArrayList<>();
        for (User u : users.findByRoleIdIn(ServiceSupport.SPONSOR_ROLES)) {
            if ("Inactive".equalsIgnoreCase(u.getStatus()) || u.getMergedIntoId() != null) continue;
            Map<String, Object> r = Map.of("id", u.getId(), "user_id", String.valueOf(u.getUserId()), "name", String.valueOf(u.getName()));
            (u.isPaysThroughRahbar() ? through : others).add(r);
        }
        Comparator<Map<String, Object>> byName = Comparator.comparing(r -> String.valueOf(r.get("name")), String.CASE_INSENSITIVE_ORDER);
        through.sort(byName);
        others.sort(byName);
        return Map.of("through_rahbar", through, "others", others);
    }

    /** Body: { through: true | false }. */
    @Transactional
    public void setThroughRahbar(Long sponsorId, boolean through) {
        User u = users.findById(sponsorId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sponsor not found."));
        if (!UserRoles.hasAny(u, ServiceSupport.SPONSOR_ROLES, users)) throw bad("That user is not a sponsor.");
        u.setPaysThroughRahbar(through);
        users.save(u);
    }

    /** Settings for the office: switched on, keys present (never the keys), test mode, receipt details, webhook address. */
    public Map<String, Object> settingsView() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", settings.enabled());
        body.put("keys_configured", gateway.configured());
        body.put("webhook_configured", gateway.webhookConfigured());
        body.put("test_mode", gateway.testMode());
        body.put("receipt", settings.receipt());
        body.put("receipt_fields", OnlinePaymentSettings.RECEIPT_FIELDS);
        body.put("receipt_ready", settings.receiptReady());
        body.put("webhook_path", "/api/public/payments/razorpay/webhook");
        return body;
    }

    // ---------------------------------------------------------------- helpers

    static String pan(Object v) {
        String p = v == null ? "" : String.valueOf(v).strip().toUpperCase(Locale.ROOT);
        if (!OnlinePaymentSettings.PAN.matcher(p).matches()) throw bad("Please enter a valid PAN (e.g. ABCDE1234F). It is needed for your 80G receipt.");
        return p;
    }

    private void notifyOffice(String title, String message) {
        List<Integer> ids = new ArrayList<>();
        String key = Section.ONLINE_PAYMENTS.key(Section.Level.EDIT);
        for (Role r : roles.findAll()) {
            if (Integer.valueOf(Role.SUPER_ADMIN).equals(r.getRoleId()) || r.permissionSet().contains(key)) ids.add(r.getRoleId());
        }
        try {
            if (!ids.isEmpty()) notifications.notifyRoles(ids, title, message, NotificationService.PAYMENT, "/admin/online-payments");
        } catch (RuntimeException e) {
            log.warn("Could not notify the office: {}", e.getMessage());
        }
    }

    static String money(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    static boolean usableEmail(String e) {
        return e != null && e.contains("@") && !e.trim().toLowerCase(Locale.ROOT).endsWith("@rahbar.com");
    }

    static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
