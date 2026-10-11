package com.rahbar.website;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** The Urdu / Hindi version of one field of a record (see WebsiteTranslatable). */
@Entity
@Table(name = "web_translations")
@Getter @Setter
public class WebTranslation extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String entityType;
    @Column(nullable = false)
    private Long entityId;
    @Column(nullable = false, length = 5)
    private String lang;
    @Column(nullable = false, length = 40)
    private String field;
    @Column(name = "text_value", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String value;
}
