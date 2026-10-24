package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Items handed out to a student. */
@Entity
@Table(name = "material_distributions")
@Getter @Setter
public class MaterialDistribution extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "given_on", nullable = false)
    private java.time.LocalDate givenOn;

    /** SCHOLAR (users.id), COACHING (coaching_students.id) or REB (reb_students.id). */
    @Column(name = "recipient_kind", nullable = false, length = 10)
    private String recipientKind;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "note", length = 500)
    private String note;
}
