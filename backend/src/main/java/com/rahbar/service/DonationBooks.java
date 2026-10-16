package com.rahbar.service;

import com.rahbar.entity.FinCategory;
import com.rahbar.entity.FinEntry;
import com.rahbar.entity.OnlinePayment;
import com.rahbar.repository.FinCategoryRepository;
import com.rahbar.repository.FinEntryRepository;
import com.rahbar.repository.WebProjectRepository;
import org.springframework.stereotype.Service;

/**
 * Books each paid website donation for the Rahbar-e-Banaat project in REB's book of income & expenses (category
 * "Online donations"). Other donations are not booked anywhere since Rahbar's own book was removed (V48); they stay
 * in Online Payments with their 80G receipts. Sponsors' online payments are never booked here.
 */
@Service
public class DonationBooks {

    static final String CATEGORY = "Online donations";
    /** The website project whose donations belong to REB's book. */
    static final String REB_PROJECT_SLUG = "rahbar-e-banaat";

    private final FinCategoryRepository categories;
    private final FinEntryRepository entries;
    private final WebProjectRepository projects;

    public DonationBooks(FinCategoryRepository categories, FinEntryRepository entries, WebProjectRepository projects) {
        this.categories = categories;
        this.entries = entries;
        this.projects = projects;
    }

    /** REB for donations to the Rahbar-e-Banaat project, else null (not booked). */
    String bookFor(Long projectId) {
        if (projectId == null) return null;
        return projects.findById(projectId).filter(p -> REB_PROJECT_SLUG.equalsIgnoreCase(p.getSlug()))
                .map(p -> FinanceService.REB).orElse(null);
    }

    public void record(OnlinePayment p, Long projectId) {
        if (!OnlinePayment.DONATION.equals(p.getPurpose()) || !OnlinePayment.PAID.equals(p.getStatus())) return;
        String book = bookFor(projectId);
        if (book == null) return;
        FinCategory c = categories.findByBookOrderByKindAscNameAsc(book).stream()
                .filter(x -> "INCOME".equals(x.getKind()) && CATEGORY.equalsIgnoreCase(x.getName())).findFirst()
                .orElseGet(() -> {
                    FinCategory n = new FinCategory();
                    n.setBook(book);
                    n.setName(CATEGORY);
                    n.setKind("INCOME");
                    return categories.save(n);
                });
        FinEntry e = new FinEntry();
        e.setBook(book);
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
