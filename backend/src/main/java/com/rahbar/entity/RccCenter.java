package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "rcc_centers")
@Getter @Setter
public class RccCenter extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rcc_center_id")
    private Long rccCenterId;

    @Column(name = "center_name", nullable = false)
    private String centerName;

    @Column(name = "incharge_name")
    private String inchargeName;

    @Column(name = "contact_number")
    private String contactNumber;

    @Column(name = "location")
    private String location;

}
