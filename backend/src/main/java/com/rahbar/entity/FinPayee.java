package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Someone a book pays (Income & Expenses > Paid to): a landlord, a teacher, a shop... One list per book. */
@Entity
@Table(name = "fin_payees")
@Getter @Setter
public class FinPayee extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** RAHBAR or REB. */
    @Column(nullable = false, length = 10)
    private String book = "RAHBAR";

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(length = 300)
    private String note;

    private boolean active = true;
}
