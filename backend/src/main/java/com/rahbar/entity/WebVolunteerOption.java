package com.rahbar.entity;

import com.rahbar.entity.WebVolunteerOption;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

/**
 * One way to help on the website's "Volunteer with us" form (Admin > Volunteers > Form options). MENTOR, TUTOR,
 * SPONSOR and CHAPTER keep their extra questions on the website; OTHER options are plain ways to help. Each can have
 * up to two lists to pick from (one item per line): MENTOR uses the first for fields, TUTOR for subjects and grades.
 */
@Entity
@Table(name = "web_volunteer_options")
@Getter @Setter
public class WebVolunteerOption extends Modifiable {

    public enum Kind { MENTOR, TUTOR, SPONSOR, CHAPTER, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Short name used in links, e.g. /volunteer?role=tutor. */
    @Column(name = "option_key", nullable = false, unique = true, length = 60)
    private String optionKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind = Kind.OTHER;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 400)
    private String description;

    @Column(length = 120)
    private String choicesLabel;

    @Column(columnDefinition = "TEXT")
    private String choices;

    @Column(length = 120)
    private String extraLabel;

    @Column(columnDefinition = "TEXT")
    private String extraChoices;

    private int displayOrder;

    private boolean active = true;

    /** One item per line, blanks dropped. */
    public static List<String> lines(String text) {
        return text == null ? List.of() : Arrays.stream(text.split("\\R")).map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
    }
}
