package com.rahbar.dto;

import com.rahbar.entity.Chapter;

public record PublicChapter(Long id, String name, String description, String leadName) {
    public static PublicChapter of(Chapter c) {
        return new PublicChapter(c.getChapterId(), c.getChapterName(), blankToNull(c.getDescription()),
                blankToNull(c.getLeadName()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
