package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Stock received for an item: how many, when, from which donor. */
@Entity
@Table(name = "material_receipts")
@Getter @Setter
public class MaterialReceipt extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "received_on", nullable = false)
    private java.time.LocalDate receivedOn;

    @Column(name = "donor_name", length = 150)
    private String donorName;

    /** The donor when they are a Rahbar user (e.g. a sponsor). */
    @Column(name = "donor_user_id")
    private Long donorUserId;

    @Column(name = "note", length = 500)
    private String note;
}
