package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A year whose income & expense summary is shown on the website. */
@Entity
@Table(name = "fin_published_years")
@Getter @Setter
public class FinPublishedYear extends Modifiable {

    @Id
    private Integer year;

    @Column(length = 500)
    private String note;
}
