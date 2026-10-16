package com.rahbar.service;

import com.rahbar.entity.FinCategory;
import com.rahbar.entity.FinEntry;
import com.rahbar.entity.OnlinePayment;
import com.rahbar.entity.WebProject;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.util.RazorpayGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Website donations: who may donate how much, and paid donations booked in Income & Expenses. */
class OnlineDonationServiceTest {

    private final OnlinePaymentRepository payments = mock(OnlinePaymentRepository.class);
    private final WebProjectRepository projects = mock(WebProjectRepository.class);
    private final OnlinePaymentService online = mock(OnlinePaymentService.class);
    private final RazorpayGateway gateway = mock(RazorpayGateway.class);
    private final OnlineDonationService service = new OnlineDonationService(payments, projects, online, gateway);

    @BeforeEach
    void setUp() {
        when(online.available()).thenReturn(true);
        when(gateway.createOrder(any(), anyString(), anyMap())).thenReturn("order_d1");
        when(payments.save(any())).thenAnswer(i -> { OnlinePayment p = i.getArgument(0); if (p.getId() == null) p.setId(3L); return p; });
        when(online.checkout(any(), anyString())).thenReturn(Map.of("order_id", "order_d1"));
        WebProject reb = new WebProject();
        reb.setId(7L);
        reb.setName("Rahbar-e-Banaat");
        reb.setActive(true);
        when(projects.findById(7L)).thenReturn(Optional.of(reb));
    }

    private Map<String, Object> form() {
        Map<String, Object> b = new HashMap<>();
        b.put("name", "Asif Khan");
        b.put("email", "asif@example.com");
        b.put("pan", "abcde1234f");
        b.put("address", "12 Boring Road, Patna 800001");
        b.put("amount", "5000");
        b.put("declaration", true);
        return b;
    }

    @Test
    void aDonationOpensAnOrderWithTheDonorsDetails() {
        Map<String, Object> f = form();
        f.put("projectId", 7);
        service.order(f);
        verify(payments, times(2)).save(argThat(p -> "DONATION".equals(p.getPurpose()) && "ABCDE1234F".equals(p.getDonorPan())
                && p.getAmount().compareTo(new BigDecimal("5000")) == 0 && Long.valueOf(7L).equals(p.getDonationProjectId())
                && "Project: Rahbar-e-Banaat".equals(p.getNote()) && p.isCitizenDeclared()));
        verify(gateway).createOrder(eq(new BigDecimal("5000.00")), eq("rahbar-don-3"), argThat(n -> "7".equals(n.get("project_id"))));
    }

    @Test
    void donationsNeedTheDeclarationPanAddressAndASensibleAmount() {
        for (String[] change : new String[][]{{"declaration", "false"}, {"pan", "12345"}, {"address", " "}, {"amount", "50"}, {"amount", "2000000"},
                {"amount", "lots"}, {"email", "not-an-email"}, {"projectId", "99"}}) {
            Map<String, Object> f = form();
            f.put(change[0], change[1]);
            assertThrows(ApiException.class, () -> service.order(f), change[0] + "=" + change[1]);
        }
        when(online.available()).thenReturn(false);
        assertThrows(ApiException.class, () -> service.order(form()));
    }

    @Test
    void aPaidDonationIsBookedAsOnlineDonationIncome() {
        FinCategoryRepository categories = mock(FinCategoryRepository.class);
        FinEntryRepository entries = mock(FinEntryRepository.class);
        WebProjectRepository projects = mock(WebProjectRepository.class);
        WebProject reb = new WebProject();
        reb.setId(7L);
        reb.setSlug("rahbar-e-banaat");
        when(projects.findById(7L)).thenReturn(Optional.of(reb));
        when(categories.findByBookOrderByKindAscNameAsc(any())).thenReturn(List.of());
        when(categories.save(any())).thenAnswer(i -> { FinCategory c = i.getArgument(0); c.setId(4L); return c; });
        OnlinePayment p = new OnlinePayment();
        p.setPurpose("DONATION");
        p.setStatus("PAID");
        p.setAmount(new BigDecimal("5000"));
        p.setDonorName("Asif Khan");
        p.setReceiptNo("RB/80G/2026-27/000003");
        p.setGatewayPaymentId("pay_x");
        p.setPaidAt(LocalDateTime.of(2026, 10, 12, 10, 0));
        new DonationBooks(categories, entries, projects).record(p, 7L);
        // a donation for the Rahbar-e-Banaat project goes into REB's book
        verify(categories).save(argThat(c -> "Online donations".equals(c.getName()) && "INCOME".equals(c.getKind()) && "REB".equals(c.getBook())));
        verify(entries).save(argThat((FinEntry e) -> "INCOME".equals(e.getKind()) && "REB".equals(e.getBook()) && e.getCategoryId() == 4L && e.getProjectId() == 7L
                && "pay_x".equals(e.getReference()) && e.getDescription().contains("RB/80G/2026-27/000003")));

        p.setPurpose("SPONSOR"); // sponsors' payments become payments to students instead
        new DonationBooks(categories, entries, projects).record(p, null);
        verify(entries, times(1)).save(any());

        p.setPurpose("DONATION"); // a general donation isn't booked: Rahbar's own book was removed (V48)
        new DonationBooks(categories, entries, projects).record(p, null);
        verify(entries, times(1)).save(any());
    }
}
