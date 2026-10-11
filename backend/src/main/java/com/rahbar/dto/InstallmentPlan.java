package com.rahbar.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** What a student's installments are built from, or why they can't be built. */
public record InstallmentPlan(Long sponsorId, int count, LocalDate start, int frequencyMonths, BigDecimal amount, String problem) {
    public boolean ok() { return problem == null; }
}
