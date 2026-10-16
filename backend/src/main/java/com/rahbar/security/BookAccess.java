package com.rahbar.security;

import org.springframework.stereotype.Component;

/**
 * Who may open a book of income & expenses: REB's book needs "REB: income & expenses" (REB_FINANCE). Rahbar's own
 * book was removed (V48). Used in @PreAuthorize as {@code @books.may(#book, 'VIEW')}.
 */
@Component("books")
public class BookAccess {

    public boolean may(String book, String level) {
        Section section = switch (book == null ? "" : book.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "reb" -> Section.REB_FINANCE;
            default -> null;
        };
        return section != null && Access.can(section, Section.Level.valueOf(level));
    }
}
