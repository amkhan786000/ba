package com.rahbar.entity;

import com.rahbar.entity.WebRevision;
import com.rahbar.service.WebsiteRevisions;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** One saved version of editable text (see WebsiteRevisions). */
@Entity
@Table(name = "web_revisions")
@Getter @Setter
public class WebRevision extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String target;
    @Column(nullable = false, length = 5)
    private String lang;
    @Column(name = "text_value", columnDefinition = "MEDIUMTEXT")
    private String value;
}
