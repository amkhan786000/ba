package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "institutions")
@Getter @Setter
public class Institution extends Modifiable {
    @Id
    @Column(name = "institution_id", length = 50)
    private String institutionId;

    @Column(name = "institution_name", nullable = false)
    private String institutionName;

    @Column(name = "address")
    private String address;

    @Column(name = "contact_number")
    private String contactNumber;

    @Column(name = "email")
    private String email;

}
