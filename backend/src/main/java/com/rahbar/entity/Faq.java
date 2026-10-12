package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A question and answer in Help & FAQ; may also be shown on the biharanjuman.com FAQ page. */
@Entity
@Table(name = "faqs")
@Getter @Setter
public class Faq extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String category;

    /** Who sees it in Rahbar: ALL, STAFF, SPONSORS or STUDENTS. */
    @Column(nullable = false, length = 10)
    private String audience = "ALL";

    @Column(nullable = false, length = 300)
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    /** Shown on the biharanjuman.com FAQ page (off unless switched on). */
    @Column(nullable = false)
    private boolean onWebsite = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int displayOrder;
}
