package com.rahbar.security;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Admin screens that permissions are granted on. A role holds keys such as "RCC_CENTERS:VIEW" and
 * "RCC_CENTERS:EDIT" (manage = add / edit / delete); EDIT implies VIEW. The Super Admin always has every key.
 */
public enum Section {
    DASHBOARD("Dashboard"),
    USERS("Users"),
    ROLES("Roles & permissions"),
    CHAPTERS("Chapters"),
    RCC_CENTERS("RCC centers"),
    COURSES("Courses & institutions"),
    PAYMENT_CONFIG("Payment config"),
    PAYMENT_DUES("Payment dues"),
    REPORTS("Reports"),
    APPLICATION_PERIOD("Application period"),
    APPLICATIONS("Applications"),
    SPONSORSHIPS("Sponsorships"),
    STUDENTS("Student directory"),
    ACTIVITY("Activity log"),
    MESSAGES("Broadcast messages"),
    /**
     * Sponsors' contact and personal details (everyone may see a sponsor's name and user ID).
     * VIEW shows them; EDIT also allows editing a sponsor's profile.
     */
    SPONSOR_DETAILS("Sponsor details (contact & personal)"),
    PROGRESS_DUE_DATES("Progress report due dates"),
    /** Per-chapter overview; with "own chapter" scope a user sees only their chapter. */
    CHAPTER_DASHBOARD("Chapter dashboard"),
    DATA_QUALITY("Data quality"),
    /**
     * Installments between sponsors and students (Admin > Payment Records). EDIT also records payments against them.
     * With "own chapter" scope a user sees only their chapter's students. Sponsors and students always see their own.
     */
    PAYMENT_RECORDS("Payment records (sponsor-student installments)"),
    /** Every email the application sent, with its text (codes and passwords hidden). */
    EMAIL_LOG("Email log"),
    /** The wording of every kind of email. EDIT adds / changes templates; only the Super Admin can delete one. */
    EMAIL_TEMPLATES("Email templates"),
    /** Graduated students and what they do now. */
    ALUMNI("Alumni"),
    /**
     * Students studying at an RCC / coaching center: their personal and family details. With "own RCC center"
     * scope (coaching center lead) only their center's students; EDIT adds and updates them.
     */
    RCC_STUDENTS("Coaching center students"),
    /**
     * People who offered to mentor, tutor, sponsor or join a chapter through the public website. VIEW lists them
     * (and notifies of new ones); EDIT sets their follow-up status and notes, or deletes a sign-up.
     */
    VOLUNTEERS("Volunteers"),
    /** The ways to help on the website's volunteer form (Admin > Volunteers > Form options). EDIT adds / changes them. */
    VOLUNTEER_OPTIONS("Volunteer form options"),

    // ---- Admin > Website: the Bihar Anjuman website (biharanjuman.com) ----
    /**
     * Blog. VIEW: write posts and see / edit one's own drafts. EDIT: see and edit every post (publishing needs
     * WEBSITE_BLOG_PUBLISH).
     */
    WEBSITE_BLOG("Website: Knowledge Hub posts"),
    WEBSITE_BLOG_PUBLISH("Website: publish Knowledge Hub posts"),
    /** The Knowledge Hub categories every post belongs to (Articles, Research, ...): add, rename, order, hide. */
    WEBSITE_BLOG_CATEGORIES("Website: Knowledge Hub categories"),
    /** Success stories; chapter / RCC-scoped users only their own chapter's / center's. Publishing needs WEBSITE_STORIES_PUBLISH. */
    WEBSITE_STORIES("Website: success stories"),
    WEBSITE_STORIES_PUBLISH("Website: publish success stories"),
    WEBSITE_GALLERY("Website: gallery"),
    /** Worldwide directory (approve sign-ups); chapter-scoped users only their chapter's members. */
    WEBSITE_DIRECTORY("Website: directory"),
    WEBSITE_PROJECTS("Website: projects"),
    /** News & events; chapter-scoped users only their chapter's. */
    WEBSITE_NEWS("Website: news & events"),
    /** Contact-form messages. */
    WEBSITE_MESSAGES("Website: messages"),
    /** Custom pages, the text of the fixed pages and its Urdu / Hindi versions. */
    WEBSITE_PAGES("Website: pages & page text"),
    /**
     * Admin > Documents: by-laws and other documents kept as text. EDIT adds, edits and archives them and sees every
     * document. Who may read a document is chosen on the document (everyone, or some roles), not here.
     */
    DOCUMENTS("Documents: add & edit (who reads each one is set on the document)"),
    /** The categories of Admin > Documents. */
    DOCUMENT_CATEGORIES("Document categories"),
    /** Rahbar-e-Banaat (the girls' Madrasa-cum-School): its dashboard. */
    REB_DASHBOARD("REB: dashboard"),
    /** Sessions, classes, sections, subjects, curriculum and settings. EDIT changes them. */
    REB_SETUP("REB: setup"),
    /** Teachers and staff, and who teaches what. */
    REB_STAFF("REB: staff"),
    /** The girls, their guardians and enrollment; EDIT admits, edits, promotes and marks girls as left. */
    REB_STUDENTS("REB: students"),
    /** The girls' documents, photo, Aadhaar (last 4 digits) and family income; EDIT uploads and removes documents. */
    REB_STUDENT_PRIVATE("REB: private details & documents"),
    /** REB reports. */
    REB_REPORTS("REB: reports"),
    /** REB fees: fee heads and amounts, bills, collecting payments, receipts, dues. */
    REB_FEES("REB: fees"),
    /** REB concessions, waiving or cancelling bills and cancelling receipts. */
    REB_FEE_APPROVALS("REB: fee concessions & cancellations"),
    /** REB sponsorships: which Rahbar sponsor supports which girl. */
    REB_SPONSORSHIPS("REB: sponsorships"),
    /** REB daily attendance and the holiday calendar. With the "own REB classes" scope: only her classes. */
    REB_ATTENDANCE("REB: attendance"),
    /** REB exams, marks, results and report cards. With the "own REB classes" scope: marks of her subjects only. */
    REB_EXAMS("REB: exams & results");

    public enum Level { VIEW, EDIT }

    private final String label;

    Section(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Permission key as stored on a role, e.g. "USERS:EDIT". */
    public String key(Level level) {
        return name() + ":" + level.name();
    }

    /** Every key (VIEW and EDIT of every section): what the Super Admin has. */
    public static Set<String> allKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (Section s : values()) {
            keys.add(s.key(Level.VIEW));
            keys.add(s.key(Level.EDIT));
        }
        return keys;
    }

    /** Keeps only known keys and adds VIEW wherever EDIT is granted. */
    public static Set<String> normalize(Iterable<String> keys) {
        Set<String> known = allKeys();
        Set<String> result = new LinkedHashSet<>();
        for (String k : keys) {
            String key = k == null ? "" : k.trim().toUpperCase();
            if (!known.contains(key)) continue;
            result.add(key);
            if (key.endsWith(":EDIT")) result.add(key.substring(0, key.length() - 4) + "VIEW");
        }
        return result;
    }
}
