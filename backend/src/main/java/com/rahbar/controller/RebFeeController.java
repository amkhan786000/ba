package com.rahbar.controller;

import com.rahbar.dto.RebPdf;
import com.rahbar.service.RebBillingService;
import com.rahbar.service.RebFeeService;
import com.rahbar.service.RebReceiptService;
import com.rahbar.service.RebSponsorshipService;
import com.rahbar.util.Rows;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Rahbar-e-Banaat phase 2: fee set-up, concessions, monthly bills, payments and receipts, ledger, dues and
 * sponsorships. Every endpoint names the permission it needs ("REB: fees", "REB: fee concessions &
 * cancellations", "REB: sponsorships").
 */
@RestController
@RequestMapping("/api/admin/reb")
@PreAuthorize("denyAll()")
public class RebFeeController {

    private final RebFeeService fees;
    private final RebBillingService billing;
    private final RebReceiptService receipts;
    private final RebSponsorshipService sponsorships;

    public RebFeeController(RebFeeService fees, RebBillingService billing, RebReceiptService receipts, RebSponsorshipService sponsorships) {
        this.fees = fees;
        this.billing = billing;
        this.receipts = receipts;
        this.sponsorships = sponsorships;
    }

    // ---------------------------------------------------------------- fee set-up

    @GetMapping("/fee-heads")
    @PreAuthorize("hasAuthority('REB_FEES:VIEW')")
    public List<Map<String, Object>> heads() {
        return fees.heads();
    }

    /** Body: { name, frequency MONTHLY | ANNUAL | ONE_TIME, residentialOnly, displayOrder, active }. */
    @PostMapping("/fee-heads")
    @PreAuthorize("hasAuthority('REB_FEES:EDIT')")
    public Map<String, Object> addHead(@RequestBody Map<String, Object> body) {
        return Map.of("message", "Fee head added.", "data", Rows.of(fees.saveHead(null, body)));
    }

    @PutMapping("/fee-heads/{id}")
    @PreAuthorize("hasAuthority('REB_FEES:EDIT')")
    public Map<String, Object> updateHead(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Map.of("message", "Fee head saved.", "data", Rows.of(fees.saveHead(id, body)));
    }

    @GetMapping("/fee-structure")
    @PreAuthorize("hasAuthority('REB_FEES:VIEW')")
    public Map<String, Object> structure(@RequestParam(required = false) Long sessionId) {
        return fees.structure(sessionId);
    }

    /** Body: { sessionId, cells: [{ classId, feeHeadId, amount, billMonth }] }. */
    @PutMapping("/fee-structure")
    @PreAuthorize("hasAuthority('REB_FEES:EDIT')")
    public Map<String, String> saveStructure(@RequestBody Map<String, Object> body) {
        fees.saveStructure(body);
        return Map.of("message", "Fees saved. They apply to bills made from now on.");
    }

    // ---------------------------------------------------------------- concessions

    @GetMapping("/students/{id}/concessions")
    @PreAuthorize("hasAuthority('REB_FEES:VIEW')")
    public List<Map<String, Object>> concessions(@PathVariable Long id) {
        return fees.concessions(id);
    }

    /** Body: { studentId, sessionId, feeHeadId, kind, value, reason, note, fromPeriod, toPeriod }. */
    @PostMapping("/concessions")
    @PreAuthorize("hasAuthority('REB_FEE_APPROVALS:EDIT')")
    public Map<String, Object> addConcession(@RequestBody Map<String, Object> body) {
        return Map.of("message", "Concession added. It applies to bills made from now on.", "data", Rows.of(fees.saveConcession(null, body)));
    }

    @PutMapping("/concessions/{id}")
    @PreAuthorize("hasAuthority('REB_FEE_APPROVALS:EDIT')")
    public Map<String, Object> updateConcession(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Map.of("message", "Concession saved.", "data", Rows.of(fees.saveConcession(id, body)));
    }

    @DeleteMapping("/concessions/{id}")
    @PreAuthorize("hasAuthority('REB_FEE_APPROVALS:EDIT')")
    public Map<String, String> deleteConcession(@PathVariable Long id) {
        fees.deleteConcession(id);
        return Map.of("message", "Concession removed.");
    }

    // ---------------------------------------------------------------- bills

    @GetMapping("/invoices/preview")
    @PreAuthorize("hasAuthority('REB_FEES:EDIT')")
    public Map<String, Object> preview(@RequestParam String period) {
        return billing.preview(period);
    }

    /** Body: { period: "2026-07" }. Only adds the bills that are missing. */
    @PostMapping("/invoices/generate")
    @PreAuthorize("hasAuthority('REB_FEES:EDIT')")
    public Map<String, Object> generate(@RequestBody Map<String, Object> body) {
        return billing.generate(body);
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAuthority('REB_FEES:VIEW')")
    public List<Map<String, Object>> invoices(@RequestParam(required = false) String period, @RequestParam(required = false) Long sectionId,
                                              @RequestParam(required = false) String status, @RequestParam(required = false) String q) {
        return billing.invoices(period, sectionId, status, q);
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAuthority('REB_FEES:VIEW')")
    public Map<String, Object> invoice(@PathVariable Long id) {
        return billing.invoice(id);
    }

    /** Body: { reason }. */
    @PostMapping("/invoices/{id}/waive")
    @PreAuthorize("hasAuthority('REB_FEE_APPROVALS:EDIT')")
    public Map<String, String> waive(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        billing.waive(id, body);
        return Map.of("message", "The rest of the bill was waived.");
    }

    /** Body: { reason }. */
    @PostMapping("/invoices/{id}/cancel")
    @PreAuthorize("hasAuthority('REB_FEE_APPROVALS:EDIT')")
    public Map<String, String> cancel(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        billing.cancel(id, body);
        return Map.of("message", "Bill cancelled. Make the month's bills again to give her a corrected one.");
    }

    // ---------------------------------------------------------------- payments

    @GetMapping("/students/{id}/due")
    @PreAuthorize("hasAuthority('REB_FEES:VIEW')")
    public Map<String, Object> due(@PathVariable Long id) {
        return billing.due(id);
    }

    /** Body: { studentId, payer, sponsorUserId, amount, paidOn, method, reference, note, invoiceIds }. */
    @PostMapping("/payments")
    @PreAuthorize("hasAuthority('REB_FEES:EDIT')")
    public Map<String, Object> collect(@RequestBody Map<String, Object> body) {
        var p = billing.collect(body);
        return Map.of("message", "Payment recorded: receipt " + p.getReceiptNo() + ".", "id", p.getId(), "receipt_no", p.getReceiptNo());
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('REB_FEES:VIEW')")
    public Map<String, Object> payments(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return billing.payments(from, to);
    }

    /** Body: { reason }. */
    @PostMapping("/payments/{id}/cancel")
    @PreAuthorize("hasAuthority('REB_FEE_APPROVALS:EDIT')")
    public Map<String, String> cancelPayment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        billing.cancelPayment(id, body);
        return Map.of("message", "Receipt cancelled; the bills it paid are unpaid again.");
    }

    @GetMapping("/payments/{id}/receipt")
    @PreAuthorize("hasAuthority('REB_FEES:VIEW')")
    public ResponseEntity<ByteArrayResource> receipt(@PathVariable Long id) {
        RebPdf pdf = receipts.receipt(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + pdf.filename() + "\"")
                .body(new ByteArrayResource(pdf.bytes()));
    }

    @GetMapping("/students/{id}/ledger")
    @PreAuthorize("hasAuthority('REB_FEES:VIEW')")
    public Map<String, Object> ledger(@PathVariable Long id) {
        return billing.ledger(id);
    }

    @GetMapping("/dues")
    @PreAuthorize("hasAuthority('REB_FEES:VIEW')")
    public List<Map<String, Object>> dues(@RequestParam(required = false) Long sectionId, @RequestParam(defaultValue = "false") boolean all) {
        return billing.dues(sectionId, all);
    }

    // ---------------------------------------------------------------- sponsorships

    @GetMapping("/sponsorships")
    @PreAuthorize("hasAuthority('REB_SPONSORSHIPS:VIEW')")
    public List<Map<String, Object>> sponsorships(@RequestParam(required = false) String status) {
        return sponsorships.list(status);
    }

    @GetMapping("/students/{id}/sponsorships")
    @PreAuthorize("hasAnyAuthority('REB_SPONSORSHIPS:VIEW', 'REB_FEES:VIEW')")
    public List<Map<String, Object>> studentSponsorships(@PathVariable Long id) {
        return sponsorships.ofStudent(id);
    }

    /** Sponsors to choose from: name and ID only. */
    @GetMapping("/sponsor-options")
    @PreAuthorize("hasAnyAuthority('REB_SPONSORSHIPS:EDIT', 'REB_FEES:EDIT')")
    public List<Map<String, Object>> sponsorOptions(@RequestParam(required = false) String q) {
        return sponsorships.sponsorOptions(q);
    }

    /** Body: { studentId, sponsorUserId, monthlyAmount, startPeriod, note }. */
    @PostMapping("/sponsorships")
    @PreAuthorize("hasAuthority('REB_SPONSORSHIPS:EDIT')")
    public Map<String, Object> startSponsorship(@RequestBody Map<String, Object> body) {
        return Map.of("message", "Sponsorship started.", "data", Rows.of(sponsorships.start(body)));
    }

    @PutMapping("/sponsorships/{id}")
    @PreAuthorize("hasAuthority('REB_SPONSORSHIPS:EDIT')")
    public Map<String, Object> updateSponsorship(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Map.of("message", "Sponsorship saved.", "data", Rows.of(sponsorships.update(id, body)));
    }

    /** Body: { endPeriod }. */
    @PostMapping("/sponsorships/{id}/end")
    @PreAuthorize("hasAuthority('REB_SPONSORSHIPS:EDIT')")
    public Map<String, Object> endSponsorship(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Map.of("message", "Sponsorship ended.", "data", Rows.of(sponsorships.end(id, body)));
    }
}
