package com.rahbar.website;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
/** A news item or event on the website. Chapter-scoped users manage only their chapter's. */
@Entity
@Table(name = "web_news")
@Getter @Setter
public class WebNews extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public enum Kind { NEWS, EVENT }

    @Column(nullable = false)
    private String title;
    @Column(nullable = false, unique = true)
    private String slug;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind = Kind.NEWS;
    @Column(length = 600)
    private String summary;
    @Column(columnDefinition = "TEXT")
    private String content;
    private LocalDate eventDate;
    private String location;
    private Long chapterId;
    private String imageUrl;
    private boolean published = true;
}
