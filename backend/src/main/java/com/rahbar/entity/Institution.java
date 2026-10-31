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

    /** What every page shows; can be edited (e.g. to the full name). */
    @Column(name = "institution_name", nullable = false)
    private String institutionName;

    /** A short code, e.g. "DRIT": set when the institution is added, then kept as it is when the name changes. */
    @Column(name = "institution_code")
    private String institutionCode;

    @Column(name = "address")
    private String address;

    @Column(name = "contact_number")
    private String contactNumber;

    @Column(name = "email")
    private String email;

}
