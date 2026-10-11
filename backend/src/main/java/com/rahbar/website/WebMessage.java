package com.rahbar.website;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A message sent through the website's contact form. */
@Entity
@Table(name = "web_messages")
@Getter @Setter
public class WebMessage extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public enum Topic { GENERAL, SPONSOR, VOLUNTEER, HELP_NEEDED, JOB_GUIDANCE, CHAPTER }

    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false)
    private String email;
    @Column(length = 40)
    private String phone;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Topic topic = Topic.GENERAL;
    @Column(nullable = false, length = 200)
    private String subject;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;
    private boolean handled;
}
