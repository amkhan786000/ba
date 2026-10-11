package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A category of organisation documents (By-laws, Policies, Minutes, ...), managed in Admin > Documents. */
@Entity
@Table(name = "document_categories")
@Getter @Setter
public class OrgDocumentCategory extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    private int displayOrder;
}
