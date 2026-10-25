package com.rahbar.dto;

/**
 * A sponsorship on behalf of an anonymous sponsor: their chapter (chapters.chapter_id), a short label to tell several
 * apart (e.g. "Donor A", never a name) and, when known, their own sponsor account (donorUserId; only the Super Admin
 * and the Application Administrator set or see it). setsDonor: donorUserId was given (null clears it); otherwise a
 * change keeps the account already linked. null wherever a sponsorship is the sponsor's own.
 */
public record OnBehalf(Long donorChapterId, String label, Long donorUserId, boolean setsDonor) {

    public OnBehalf(Long donorChapterId, String label) {
        this(donorChapterId, label, null, false);
    }
}
