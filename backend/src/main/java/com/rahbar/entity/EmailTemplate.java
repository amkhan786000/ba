package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A custom subject and text for one kind of email (see service.EmailType); without one the built-in default is used. */
@Entity
@Table(name = "email_templates")
@Getter @Setter
public class EmailTemplate extends Modifiable {
    /** EmailType name, e.g. PAYMENT_OVERDUE. */
    @Id
    @Column(name = "template_key", length = 60)
    private String templateKey;

    @Column(name = "subject", nullable = false, length = 300)
    private String subject;

    @Lob
    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;
}
