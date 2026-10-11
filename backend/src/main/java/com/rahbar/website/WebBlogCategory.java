package com.rahbar.website;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * A section of the website's Media & Knowledge Hub (Articles, Research, Case Studies, ...). Every blog post has one.
 * Managed by blog publishers in Admin > Website > Blog > Categories; inactive ones are hidden on the website.
 */
@Entity
@Table(name = "web_blog_categories")
@Getter @Setter
public class WebBlogCategory extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(length = 600)
    private String description;

    private int displayOrder;

    private boolean active = true;
}
