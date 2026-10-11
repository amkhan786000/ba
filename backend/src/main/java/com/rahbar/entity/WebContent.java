package com.rahbar.entity;

import com.rahbar.entity.WebContent;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Edited text of one slot of the website's fixed pages (e.g. "home.hero.title") in one language. */
@Entity
@Table(name = "web_content")
@Getter @Setter
public class WebContent extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_key", nullable = false, length = 100)
    private String key;
    @Column(nullable = false, length = 5)
    private String lang;
    @Column(name = "text_value", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String value;
}
