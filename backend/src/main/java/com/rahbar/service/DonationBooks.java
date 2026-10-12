package com.rahbar.service;

import com.rahbar.entity.FinCategory;
import com.rahbar.entity.FinEntry;
import com.rahbar.entity.OnlinePayment;
import com.rahbar.repository.FinCategoryRepository;
import com.rahbar.repository.FinEntryRepository;
import org.springframework.stereotype.Service;

/**
 * Books each paid website donation in Administration > Income & Expenses (category "Online donations", the chosen
 * project if any), so the totals and the website's "Where the money goes" include it. Sponsors' online payments are
 * not booked here: they become payments to students, which the summary already counts as scholarships.
 */
@Service
public class DonationBooks {

    static final String CATEGORY = "Online donations";

    private final FinCategoryRepository categories;
    private final FinEntryRepository entries;

    public DonationBooks(FinCategoryRepository categories, FinEntryRepository entries) {
        this.categories = categories;
        this.entries = entries;
    }

    public void record(OnlinePayment p, Long projectId) {
        if (!OnlinePayment.DONATION.equals(p.getPurpose()) || !OnlinePayment.PAID.equals(p.getStatus())) return;
        FinCategory c = categories.findAllByOrderByKindAscNameAsc().stream()
                .filter(x -> "INCOME".equals(x.getKind()) && CATEGORY.equalsIgnoreCase(x.getName())).findFirst()
                .orElseGet(() -> {
                    FinCategory n = new FinCategory();
                    n.setName(CATEGORY);
                    n.setKind("INCOME");
                    return categories.save(n);
                });
        FinEntry e = new FinEntry();
        e.setEntryDate(p.getPaidAt().toLocalDate());
        e.setKind("INCOME");
        e.setCategoryId(c.getId());
        e.setProjectId(projectId);
        e.setAmount(p.getAmount());
        e.setParty(p.getDonorName());
        e.setDescription("Online donation, 80G receipt " + p.getReceiptNo() + (p.getNote() == null ? "" : " (" + p.getNote() + ")"));
        e.setMethod("BANK");
        e.setReference(p.getGatewayPaymentId());
        entries.save(e);
    }
}
