package com.rahbar.entity;

import com.rahbar.entity.WebPage;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A custom page of the website (optionally in its menu). */
@Entity
@Table(name = "web_pages")
@Getter @Setter
public class WebPage extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(columnDefinition = "MEDIUMTEXT")
    private String body;
    private boolean published;
    private boolean showInMenu;
    private int menuOrder;
}
