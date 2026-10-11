package com.rahbar.dto;

import com.rahbar.entity.RccCenter;

public record PublicCenter(Long id, String name, String location, String inchargeName) {
    public static PublicCenter of(RccCenter c) {
        return new PublicCenter(c.getRccCenterId(), c.getCenterName(), blankToNull(c.getLocation()),
                blankToNull(c.getInchargeName()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
