package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * An organisation document (by-laws, policies, ...): text pasted in, and / or an attached PDF or Word file. Admin > Documents.
 * Named OrgDocument so it doesn't clash with the PDF library's Document class.
 */
@Entity
@Table(name = "documents")
@Getter @Setter
public class OrgDocument extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 200)
    private String title;

    /** The text as pasted (empty when the document is only its attached file); line breaks are kept and Markdown (## heading, **bold**, lists) is shown formatted. */
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    /** An attached PDF / Word file (the stored file name), if any; shown for download beside (or instead of) the text. */
    @Column(length = 255)
    private String attachmentPath;

    /** The attached file's name as uploaded, e.g. "By-laws 2024.pdf". */
    @Column(length = 255)
    private String attachmentName;

    /** When the document (or this version of it) took effect, e.g. the date by-laws were adopted. */
    private java.time.LocalDate effectiveDate;

    /** Kept but hidden from the normal list. */
    private boolean archived;

    /** PUBLIC: every signed-in user; ROLES: only the roles in visibleRoleIds (editors always see every document). */
    @Column(nullable = false, length = 10)
    private String visibility = "PUBLIC";

    /** Comma-separated role ids that may read a ROLES document. */
    @Column(length = 500)
    private String visibleRoleIds;

    /** The role ids of visibleRoleIds. */
    public java.util.Set<Integer> visibleRoles() {
        java.util.Set<Integer> out = new java.util.LinkedHashSet<>();
        if (visibleRoleIds == null) return out;
        for (String s : visibleRoleIds.split(",")) {
            try {
                if (!s.isBlank()) out.add(Integer.valueOf(s.strip()));
            } catch (NumberFormatException ignored) {
                // not a role id
            }
        }
        return out;
    }
}
