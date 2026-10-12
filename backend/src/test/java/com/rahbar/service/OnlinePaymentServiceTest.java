package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.util.RazorpayGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Sponsors paying through Rahbar: who may pay what, and a payment recorded exactly once. */
class OnlinePaymentServiceTest {

    private final OnlinePaymentRepository payments = mock(OnlinePaymentRepository.class);
    private final OnlinePaymentItemRepository items = mock(OnlinePaymentItemRepository.class);
    private final PaymentInstallmentRepository installments = mock(PaymentInstallmentRepository.class);
    private final PaymentRepository paymentRecords = mock(PaymentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final GrantorGranteeRepository mappings = mock(GrantorGranteeRepository.class);
    private final PaymentInstallmentService installmentService = mock(PaymentInstallmentService.class);
    private final RazorpayGateway gateway = mock(RazorpayGateway.class);
    private final OnlinePaymentSettings settings = mock(OnlinePaymentSettings.class);
    private final OnlinePaymentService service = new OnlinePaymentService(payments, items, installments, paymentRecords, users, mappings,
            mock(RoleRepository.class), installmentService, gateway, settings, mock(OnlinePaymentReceipts.class), mock(EmailService.class),
            mock(NotificationService.class));

    private final User sponsor = new User();
    private final PaymentInstallment due = new PaymentInstallment();

    @BeforeEach
    void setUp() {
        sponsor.setId(5L);
        sponsor.setUserId("SP5");
        sponsor.setName("Zara");
        sponsor.setPaysThroughRahbar(true);
        when(users.findById(5L)).thenReturn(Optional.of(sponsor));
        when(settings.enabled()).thenReturn(true);
        when(settings.get(anyString())).thenReturn("");
        when(gateway.configured()).thenReturn(true);
        when(gateway.createOrder(any(), anyString(), anyMap())).thenReturn("order_9");
        GrantorGrantee g = new GrantorGrantee();
        g.setGrantorId(5L);
        g.setGranteeId(11L);
        when(mappings.findByGrantorId(5L)).thenReturn(List.of(g));
        due.setInstallmentId(100L);
        due.setInstallmentNo(3);
        due.setGrantorId(5L);
        due.setGranteeId(11L);
        due.setAmount(new BigDecimal("14000"));
        due.setDueDate(LocalDate.now().plusDays(5));
        when(installments.findById(100L)).thenReturn(Optional.of(due));
        when(payments.save(any())).thenAnswer(i -> { OnlinePayment p = i.getArgument(0); if (p.getId() == null) p.setId(7L); return p; });
    }

    private Map<String, Object> order(Object pan, boolean declaration) {
        Map<String, Object> b = new HashMap<>();
        b.put("installmentIds", List.of(100));
        b.put("pan", pan);
        b.put("declaration", declaration);
        return b;
    }

    @Test
    void aSponsorWhoPaysThroughRahbarGetsAnOrderForTheChosenInstallments() {
        Map<String, Object> co = service.sponsorOrder(5L, order("abcde1234f", true));
        assertEquals("order_9", co.get("order_id"));
        assertEquals(1400000L, co.get("amount"));
        assertEquals("ABCDE1234F", sponsor.getPan());
        verify(items).save(argThat(it -> it.getInstallmentId() == 100L && it.getStudentUserId() == 11L));
    }

    @Test
    void ordersNeedTheRightSponsorDeclarationPanAndUnpaidInstallments() {
        assertThrows(ApiException.class, () -> service.sponsorOrder(5L, order("ABCDE1234F", false)));
        assertThrows(ApiException.class, () -> service.sponsorOrder(5L, order("12345", true)));
        due.setPaymentId(1L);
        assertThrows(ApiException.class, () -> service.sponsorOrder(5L, order("ABCDE1234F", true)));
        due.setPaymentId(null);
        due.setGrantorId(6L); // someone else's student
        assertThrows(ApiException.class, () -> service.sponsorOrder(5L, order("ABCDE1234F", true)));
        due.setGrantorId(5L);
        sponsor.setPaysThroughRahbar(false);
        assertThrows(ApiException.class, () -> service.sponsorOrder(5L, order("ABCDE1234F", true)));
        sponsor.setPaysThroughRahbar(true);
        when(settings.enabled()).thenReturn(false);
        assertThrows(ApiException.class, () -> service.sponsorOrder(5L, order("ABCDE1234F", true)));
    }

    @Test
    void aBadSignatureRecordsNothing() {
        when(gateway.paymentSignatureValid("order_9", "pay_1", "bad")).thenReturn(false);
        assertThrows(ApiException.class, () -> service.confirm("order_9", "pay_1", "bad"));
        verify(payments, never()).lockByGatewayOrderId(anyString());
    }

    @Test
    void aPaymentIsRecordedOnceWithItsInstallmentAndReceipt() {
        OnlinePayment p = new OnlinePayment();
        p.setId(7L);
        p.setPurpose(OnlinePayment.SPONSOR);
        p.setSponsorUserId(5L);
        p.setDonorName("Zara");
        p.setAmount(new BigDecimal("14000"));
        p.setGatewayOrderId("order_9");
        when(payments.lockByGatewayOrderId("order_9")).thenReturn(Optional.of(p));
        OnlinePaymentItem it = new OnlinePaymentItem();
        it.setOnlinePaymentId(7L);
        it.setInstallmentId(100L);
        it.setStudentUserId(11L);
        it.setAmount(new BigDecimal("14000"));
        when(items.findByOnlinePaymentId(7L)).thenReturn(List.of(it));
        when(paymentRecords.saveAndFlush(any())).thenAnswer(i -> { Payment pay = i.getArgument(0); pay.setPaymentId(55L); return pay; });
        when(gateway.paymentSignatureValid("order_9", "pay_1", "good")).thenReturn(true);

        OnlinePayment paid = service.confirm("order_9", "pay_1", "good");
        assertEquals("PAID", paid.getStatus());
        assertEquals("pay_1", paid.getGatewayPaymentId());
        assertTrue(paid.getReceiptNo().startsWith("RB/80G/"));
        verify(paymentRecords).saveAndFlush(argThat(pay -> "ONLINE".equals(pay.getMethod()) && "Paid".equals(pay.getStatus()) && pay.getGrantorId() == 5L));
        verify(installmentService).afterPayment(any(), eq(100L));
        assertEquals(55L, it.getPaymentId());

        service.confirm("order_9", "pay_1", "good"); // the webhook or a second click: nothing more
        verify(paymentRecords, times(1)).saveAndFlush(any());
    }

    @Test
    void receiptNumbersFollowTheFinancialYearAndAmountsAreInWords() {
        assertEquals("RB/80G/2026-27/000007", OnlinePaymentReceipts.receiptNo(7L, LocalDateTime.of(2026, 10, 12, 9, 0)));
        assertEquals("RB/80G/2025-26/000007", OnlinePaymentReceipts.receiptNo(7L, LocalDateTime.of(2026, 3, 31, 9, 0)));
        assertEquals("Rupees Fourteen Thousand Only", OnlinePaymentReceipts.Words.inr(new BigDecimal("14000")));
        assertEquals("Rupees One Lakh Twenty Five Thousand Five Hundred One and Fifty Paise Only",
                OnlinePaymentReceipts.Words.inr(new BigDecimal("125501.50")).replaceAll("\\s+", " "));
    }
}
