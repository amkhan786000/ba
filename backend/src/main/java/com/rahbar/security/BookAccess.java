package com.rahbar.security;

import org.springframework.stereotype.Component;

/**
 * Who may open which book of income & expenses: the Rahbar book needs "Rahbar income & expenses" (FINANCE), the REB
 * book "REB: income & expenses" (REB_FINANCE). Used in @PreAuthorize as {@code @books.may(#book, 'VIEW')}.
 */
@Component("books")
public class BookAccess {

    public boolean may(String book, String level) {
        Section section = switch (book == null ? "" : book.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "rahbar" -> Section.FINANCE;
            case "reb" -> Section.REB_FINANCE;
            default -> null;
        };
        return section != null && Access.can(section, Section.Level.valueOf(level));
    }
}
