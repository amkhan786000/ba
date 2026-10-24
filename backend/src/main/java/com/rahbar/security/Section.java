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
    /**
     * Sponsors & Payments > Online Payments (Razorpay): VIEW lists online payments and donations; EDIT records passing
     * the money on to students, chooses which sponsors pay through Rahbar and keeps the 80G receipt details.
     */
    ONLINE_PAYMENTS("Online payments & donations"),
    PAYMENT_DUES("Payment dues"),
    REPORTS("Reports"),
    APPLICATION_PERIOD("Application period"),
    APPLICATIONS("Applications"),
    /**
     * Applications > Apply for Sponsorship, while an application period is open. VIEW: a sponsor applies for themselves
     * (how many students, and what kind). EDIT: applies on behalf of any sponsor (chapter-scoped: their chapter's) and
     * sees everyone's applications.
     */
    SPONSOR_APPLY("Apply for sponsorship"),
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
    /** Notes students write to their sponsors (Sponsors & Payments > Notes to sponsors). VIEW reads them; EDIT approves or returns them. */
    STUDENT_NOTES("Notes to sponsors"),
    /**
     * Coaching Center > Students (classes 7 to 10, their admission details). With "own RCC center" scope (coaching
     * center coordinator, coaching teacher) only their center's students; EDIT admits and updates them.
     */
    RCC_STUDENTS("Coaching center students"),
    /** Coaching Center > Teachers (classes 7-10). With "own RCC center" scope only their center's; EDIT adds and updates them. */
    COACHING_TEACHERS("Coaching center teachers"),
    /**
     * Coaching Center > Exams: monthly exams per class, their subjects and marks, and the 1st / 2nd / 3rd prizes.
     * EDIT sets up exams, enters marks, generates the prizes and manages the subject list.
     */
    COACHING_EXAMS("Coaching center exams & marks"),
    /** Coaching Center > Attendance: the daily register. EDIT marks it (coordinators and coaching teachers by default). */
    COACHING_ATTENDANCE("Coaching center attendance"),
    /**
     * Students > Early Warning: scholarship and coaching students showing signs of dropping out (low or falling marks,
     * overdue reports or installments, absences). Chapter / RCC-scoped users see their own students. EDIT records
     * follow-up calls and visits.
     */
    EARLY_WARNING("Early warning & follow-ups"),
    /**
     * People who offered to mentor, tutor, sponsor or join a chapter through the public website. VIEW lists them
     * (and notifies of new ones); EDIT sets their follow-up status and notes, or deletes a sign-up.
     */
    VOLUNTEERS("Volunteers"),
    /** The ways to help on the website's volunteer form (Admin > Volunteers > Form options). EDIT adds / changes them. */
    VOLUNTEER_OPTIONS("Volunteer form options"),
    /**
     * Volunteers and graduates paired with scholarship students as mentors / tutors (Students > Mentoring). VIEW lists
     * the pairs and the suggested matches; EDIT pairs, ends a pairing and keeps notes.
     */
    MENTORING("Mentoring"),

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
    /** Help & FAQ: EDIT adds, changes and deletes questions and chooses which show on biharanjuman.com (everyone reads them). */
    FAQS("FAQ: add, edit & delete questions"),
    /** Administration > Application Settings: how the app shows things to everyone (e.g. the date format). */
    APP_SETTINGS("Application settings (date format, ...)"),
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
    /** Rahbar-e-Banaat > REB Income & Expenses: REB's own book (separate from Rahbar's). EDIT records entries. */
    REB_FINANCE("REB: income & expenses"),
    /** REB concessions, waiving or cancelling bills and cancelling receipts. */
    REB_FEE_APPROVALS("REB: fee concessions & cancellations"),
    /** REB sponsorships: which Rahbar sponsor supports which girl. */
    REB_SPONSORSHIPS("REB: sponsorships"),
    /** REB daily attendance and the holiday calendar. With the "own REB classes" scope: only her classes. */
    REB_ATTENDANCE("REB: attendance"),
    /** REB exams, marks, results and report cards. With the "own REB classes" scope: marks of her subjects only. */
    REB_EXAMS("REB: exams & results"),
    /** REB admission enquiries from the website, phone or visits; EDIT follows them up and admits. */
    REB_ENQUIRIES("REB: admission enquiries"),
    /** REB Quran / Hifz progress (sabaq, sabqi, manzil). With the "own REB classes" scope: her classes only. */
    REB_QURAN("REB: Quran / Hifz progress"),
    /** REB hostel leave and gate passes. */
    REB_HOSTEL("REB: hostel leave & gate passes");

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
