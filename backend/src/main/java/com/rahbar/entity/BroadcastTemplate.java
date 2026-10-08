package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A saved subject + message to reuse in Admin > Broadcast Messages. */
@Entity
@Table(name = "broadcast_templates")
@Getter @Setter
public class BroadcastTemplate extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;
}
