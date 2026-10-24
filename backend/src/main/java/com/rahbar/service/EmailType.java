package com.rahbar.service;

import java.util.List;

/**
 * Every kind of email the application sends, with its built-in wording. Admin > Email Templates can replace the
 * subject and text of each one (stored in email_templates); deleting the replacement goes back to this default.
 * <p>
 * {@code {{placeholder}}} values are filled in when the email is sent; {@code {{name}}} is always the recipient's
 * name. {@code required} placeholders must stay in a custom template (e.g. the one-time code).
 */
public enum EmailType {

    // ------------------------------------------------------------------ sign-in and account
    LOGIN_OTP("Account", "Sign-in code",
            "The one-time code sent at every sign-in.",
            "Your Login OTP",
            "Your OTP for login is {{code}}. It is valid for 5 minutes.",
            List.of("name", "code"), List.of("code")),
    PASSWORD_RESET_CODE("Account", "Forgot password: reset code",
            "Sent when someone asks to reset a forgotten password.",
            "Rahbar: your password reset code",
            "Dear {{name}},\n\nYour code to reset your Rahbar password is: {{code}}\n\nIt is valid for {{minutes}} minutes. "
                    + "If you didn't ask for this, ignore this email; your password stays the same.\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "code", "minutes"), List.of("code")),
    PASSWORD_RESET_BY_ADMIN("Account", "Password reset by an administrator",
            "Sent with a temporary password when an administrator resets someone's password (Manage Users).",
            "Rahbar: your password was reset",
            "Dear {{name}},\n\nAn administrator reset your Rahbar password. Sign in with this temporary password:\n\n"
                    + "    {{password}}\n\nYou will be asked to choose your own password straight after signing in.\n\n"
                    + "Regards,\nRahbar - Bihar Anjuman",
            List.of("name", "password"), List.of("password")),
    ACCOUNT_CREATED("Account", "New account (Manage Users)",
            "Sent with a temporary password when an administrator adds a user in Manage Users.",
            "Rahbar: your account has been created",
            "Dear {{name}},\n\nAn account has been created for you on Rahbar as {{role}}.\n\n"
                    + "Sign in at: {{sign_in_url}}\nEmail: {{email}}\nUser ID: {{user_id}}\nTemporary password: {{password}}\n\n"
                    + "You will be asked to choose your own password straight after signing in.\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "role", "sign_in_url", "email", "user_id", "password"), List.of("password")),
    ADMIN_ACCOUNT_CREATED("Account", "First administrator account",
            "Sent once, when the application creates the first Super Admin on an empty database.",
            "Your Rahbar administrator account",
            "An administrator account was created for you on Rahbar{{site}}.\n\nE-mail: {{email}}\nTemporary password: {{password}}\n\n"
                    + "After signing in you will receive a one-time code by e-mail, and then you must choose a new password.",
            List.of("name", "email", "password", "site"), List.of("password")),
    PASSWORD_CHANGED("Account", "Password changed",
            "Sent after someone changes their password from their profile.",
            "Rahbar: Password changed",
            "Dear {{name}},\n\nYour Rahbar password was changed. If this wasn't you, contact the administrator straight away."
                    + "\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name"), List.of()),
    PASSWORD_RESET_DONE("Account", "Password reset with \"Forgot password\"",
            "Sent after someone sets a new password with a reset code.",
            "Rahbar: Password changed",
            "Dear {{name}},\n\nYour Rahbar password was reset using \"Forgot password\". If this wasn't you, contact the "
                    + "administrator straight away.\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name"), List.of()),

    // ------------------------------------------------------------------ sponsor mapping
    SPONSOR_STUDENT_MAPPED("Sponsorship", "To sponsor: new student mapped",
            "Sent to a sponsor when a student is mapped to them.",
            "Rahbar: New student mapped to you",
            "Dear {{name}},\n\n{{student_name}} ({{student_code}}) is now one of your sponsored students. You can see their "
                    + "payment schedule on your Payments page.\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "student_name", "student_code"), List.of()),
    STUDENT_SPONSOR_ASSIGNED("Sponsorship", "To student: sponsor assigned",
            "Sent to a student when they are mapped to a sponsor (also when the sponsor changes).",
            "Rahbar: You have a sponsor",
            "Dear {{name}},\n\n{{sponsor_name}} is now your sponsor. You can see your payment schedule on your Payments page."
                    + "\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "sponsor_name"), List.of()),
    SPONSOR_RENEWAL("Sponsorship", "To sponsor: will you continue next session?",
            "Sent when the office starts a sponsor renewal campaign (Sponsors & Payments > Sponsor Renewal), and as a reminder.",
            "Rahbar: will you continue sponsoring in {{session_year}}?",
            "Dear {{name}},\n\nThank you for sponsoring {{students}} with Rahbar. As we plan the {{session_year}} session, please "
                    + "tell us whether you will continue: with the same students, with a different number of students, or not "
                    + "this time.\n\n{{message}}\n\nPlease answer by {{deadline}} on your Rahbar sponsor page:\n{{answer_url}}"
                    + "\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "students", "session_year", "deadline", "message", "answer_url"), List.of("answer_url")),
    SPONSOR_STUDENT_NOTE("Sponsorship", "To sponsor: a note from their student",
            "Sent to a sponsor when the office approves a note their student wrote to them.",
            "Rahbar: a note from {{student_name}}",
            "Dear {{name}},\n\n{{student_name}} wrote to you:\n\n\"{{message}}\"\n\nYou can see all notes from your students on "
                    + "your dashboard.\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "student_name", "message"), List.of()),
    SPONSOR_FOLLOWUP_OVERDUE("Sponsorship", "Follow-up: sponsor's payments overdue",
            "Sent from Sponsor Follow-up to a sponsor whose installments are overdue (a personal note, besides the automatic reminders).",
            "Rahbar: a gentle reminder about your student",
            "Dear {{name}},\n\nWe hope you are well. We noticed that {{overdue_count}} installment(s) for your sponsored "
                    + "student(s) are still unpaid (the oldest was due on {{oldest_due}}).\n\n{{message}}\n\n"
                    + "If you have already paid, please let us know so we can update our records. If anything has changed for you, "
                    + "just reply to this email.\n\nThank you for supporting education,\nRahbar - Bihar Anjuman",
            List.of("name", "overdue_count", "oldest_due", "message"), List.of()),
    SPONSOR_INVITE_ANOTHER("Sponsorship", "Follow-up: would you sponsor another student?",
            "Sent from Sponsor Follow-up to a sponsor who has no student at the moment.",
            "Rahbar: would you sponsor another student?",
            "Dear {{name}},\n\nThank you for sponsoring {{last_student}} through Rahbar. Your support made a real difference.\n\n"
                    + "Many students are waiting for a sponsor this year. Would you like to support another student? Reply to this "
                    + "email and our team will suggest a student for you.\n\n{{message}}\n\nWith gratitude,\nRahbar - Bihar Anjuman",
            List.of("name", "last_student", "message"), List.of()),
    SPONSOR_PROGRESS_UPLOADED("Sponsorship", "To sponsor: new progress report",
            "Sent to a sponsor when their student uploads marks / a progress report.",
            "Rahbar: {{student_name}} uploaded a progress report",
            "Dear {{name}},\n\n{{student_name}} uploaded their marks ({{marks}}) for {{session}} {{year}}. You can see the report "
                    + "on your Student Progress page.\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "student_name", "marks", "session", "year"), List.of()),
    SPONSOR_SPENT_PROOF_UPLOADED("Sponsorship", "To sponsor: spent proof uploaded",
            "Sent to a sponsor when their student uploads proof of how a payment was spent.",
            "Rahbar: {{student_name}} sent proof for installment {{installment}}",
            "Dear {{name}},\n\n{{student_name}} uploaded proof of how installment {{installment}} was spent. You can open it "
                    + "on your Payments page.\n\nThank you for your support,\nRahbar - Bihar Anjuman",
            List.of("name", "student_name", "installment"), List.of()),
    SPONSOR_STUDENT_GRADUATED("Sponsorship", "To sponsor: student graduated",
            "Sent to a student's sponsor when the office marks the student as graduated.",
            "Rahbar: {{student_name}} has graduated",
            "Dear {{name}},\n\nGood news: {{student_name}} has completed their studies and graduated. Your support made "
                    + "this possible.\n\nThank you for being part of their journey,\nRahbar - Bihar Anjuman",
            List.of("name", "student_name"), List.of()),
    SPONSOR_STUDENT_UNMAPPED("Sponsorship", "To previous sponsor: student moved on",
            "Sent to a sponsor when a student is no longer mapped to them.",
            "Rahbar: Student no longer mapped to you",
            "Dear {{name}},\n\n{{student_name}} ({{student_code}}) is no longer one of your sponsored students. "
                    + "Installments you already paid stay in your payment history.\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "student_name", "student_code"), List.of()),
    STUDENT_SPONSOR_REMOVED("Sponsorship", "To student: no sponsor any more",
            "Sent to a student whose sponsor was removed without a new one.",
            "Rahbar: Sponsor changed",
            "Dear {{name}},\n\nYou are currently not mapped to a sponsor. The office will let you know when a new sponsor is "
                    + "assigned.\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name"), List.of()),

    // ------------------------------------------------------------------ payments
    PAYMENT_RECEIVED("Payments", "To student: payment received",
            "Sent to a student when their sponsor records a payment.",
            "Rahbar: Payment received",
            "Dear {{name}},\n\n{{sponsor_name}} paid installment {{installment}} for you. Please upload your proof of receipt."
                    + "\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "sponsor_name", "installment"), List.of()),
    PAYMENT_OVERDUE("Payments", "To sponsor: payment overdue",
            "Daily reminder to a sponsor, once per overdue installment.",
            "Rahbar: Payment overdue",
            "Dear {{name}},\n\n{{count}} installment(s) for {{student_name}} ({{student_code}}) are overdue. Please record the "
                    + "payment once it is made.\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "count", "student_name", "student_code"), List.of()),
    PAYMENT_DUE_SOON("Payments", "To sponsor: payment due soon",
            "Reminder to a sponsor a week before the next installment.",
            "Rahbar: Payment due soon",
            "Dear {{name}},\n\nThe next installment for {{student_name}} ({{student_code}}) is due on {{due_date}}."
                    + "\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "student_name", "student_code", "due_date"), List.of()),

    ONLINE_PAYMENT_RECEIPT("Payments", "Online payment: 80G receipt",
            "Sent with the 80G receipt (PDF) when a sponsor pays online through Rahbar or someone donates on the website.",
            "Bihar Anjuman: receipt {{receipt_no}} for your payment of Rs. {{amount}}",
            "Dear {{name}},\n\nThank you! We received your payment of Rs. {{amount}} on {{date}}. Your 80G receipt {{receipt_no}} "
                    + "is attached.\n\n{{details}}\n\nWith gratitude,\nBihar Anjuman",
            List.of("name", "amount", "date", "receipt_no", "details"), List.of()),
    STUDENT_PAID_THROUGH_RAHBAR("Payments", "To student: money passed on by Rahbar",
            "Sent to a student when the office sends them money their sponsor paid online through Rahbar.",
            "Rahbar: installment {{installment}} sent to your bank account",
            "Dear {{name}},\n\nYour sponsor paid installment {{installment}} for your studies through Rahbar. We sent it to your bank account on "
                    + "{{date}} (reference {{reference}}).\n\nPlease upload proof of how it was spent on your Payments page.\n\n"
                    + "Regards,\nRahbar - Bihar Anjuman",
            List.of("name", "installment", "date", "reference"), List.of()),
    SPONSOR_STATEMENT("Payments", "To sponsor: yearly statement",
            "Sent with the sponsor's yearly statement (PDF attached) when the office emails it.",
            "Rahbar: your sponsorship statement for {{year}}",
            "Dear {{name}},\n\nPlease find attached your sponsorship statement for {{year}}: the installments due, the payments "
                    + "you made and your students' latest progress reports.\n\nThank you for your support.\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "year"), List.of()),

    // ------------------------------------------------------------------ students
    PROGRESS_APPROVED("Students", "Progress report approved",
            "Sent to a student when their sponsor approves a progress report.",
            "Rahbar: Progress report approved",
            "Dear {{name}},\n\n{{reviewer}} approved your progress report for {{session}} ({{year}})."
                    + "\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "reviewer", "session", "year"), List.of()),
    PROGRESS_RETURNED("Students", "Progress report needs changes",
            "Sent to a student when a progress report is sent back.",
            "Rahbar: Progress report needs changes",
            "Dear {{name}},\n\n{{reviewer}} sent back your progress report for {{session}} ({{year}}): {{comment}}"
                    + "\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "reviewer", "session", "year", "comment"), List.of()),
    PROGRESS_DUE_SOON("Students", "Progress report due soon",
            "Reminder two weeks before a progress report due date.",
            "Rahbar: Progress report due {{due_date}}",
            "Dear {{name}},\n\nPlease upload your progress report ({{title}}) by {{due_date}}. {{note}}"
                    + "\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "title", "due_date", "note"), List.of()),
    PROGRESS_OVERDUE("Students", "Progress report overdue",
            "Reminder after a progress report due date has passed.",
            "Rahbar: Progress report overdue",
            "Dear {{name}},\n\nYour progress report ({{title}}) was due on {{due_date}}. Please upload it as soon as possible."
                    + "\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "title", "due_date"), List.of()),
    BANK_DETAILS_MISSING("Students", "Bank details missing",
            "Monthly reminder to students without bank details.",
            "Rahbar: Please add your bank details",
            "Dear {{name}},\n\nWe don't have your bank details yet, so payments can't be sent to you. Please add them on your "
                    + "Payments page.\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name"), List.of()),

    // ------------------------------------------------------------------ applications
    APPLICATION_STATUS("Applications", "Application status changed",
            "Sent to an applicant when their application status changes.",
            "Rahbar: Application update",
            "Dear {{name}},\n\nYour scholarship application #{{application_id}} is now '{{status}}'. {{comments}}"
                    + "\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "application_id", "status", "comments"), List.of()),
    COACHING_SCHOLARSHIP_INVITE("Applications", "To a Class 10 coaching student: apply for the scholarship",
            "Sent from Coaching Center > Ready for Scholarship to invite a Class 10 coaching student to apply.",
            "Bihar Anjuman: apply for the Rahbar scholarship",
            "Dear {{name}},\n\nWell done on your studies at {{center}}. Applications for the Rahbar Diploma & Paramedical "
                    + "Sponsorship are open, and we would like you to apply:\n\n{{apply_url}}\n\nAsk your coaching center "
                    + "if you need help with the form.\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "center", "apply_url"), List.of("apply_url")),
    INTERVIEW_SCHEDULED("Applications", "Interview scheduled",
            "Sent to an applicant when their interview is scheduled.",
            "Rahbar: Interview scheduled",
            "Dear {{name}},\n\nYour scholarship interview is scheduled: Interview on {{interview_at}} at {{venue}}."
                    + "\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "interview_at", "venue"), List.of()),

    // ------------------------------------------------------------------ general
    BROADCAST("General", "Broadcast message",
            "The frame around every broadcast message (Admin > Broadcast Messages). {{message}} is the text you write there.",
            "Rahbar: {{subject}}",
            "Dear {{name}},\n\n{{message}}\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "subject", "message"), List.of("message")),
    GENERAL_NOTIFICATION("General", "Other notifications",
            "Any other notification that is also emailed.",
            "Rahbar: {{title}}",
            "Dear {{name}},\n\n{{message}}\n\nRegards,\nRahbar - Bihar Anjuman",
            List.of("name", "title", "message"), List.of("message"));

    public final String group;
    public final String label;
    public final String description;
    public final String defaultSubject;
    public final String defaultBody;
    public final List<String> placeholders;
    public final List<String> required;

    EmailType(String group, String label, String description, String defaultSubject, String defaultBody,
              List<String> placeholders, List<String> required) {
        this.group = group;
        this.label = label;
        this.description = description;
        this.defaultSubject = defaultSubject;
        this.defaultBody = defaultBody;
        this.placeholders = placeholders;
        this.required = required;
    }
}
