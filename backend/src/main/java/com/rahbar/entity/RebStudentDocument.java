package com.rahbar.entity;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** An uploaded document of a girl (birth certificate, Aadhaar, photo, TC, ...). */
@Entity
@Table(name = "reb_student_documents")
@Getter @Setter
public class RebStudentDocument extends Modifiable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long studentId;
    @Column(nullable = false, length = 20)
    private String kind;
    @Column(nullable = false, length = 255)
    private String filePath;
    @Column(length = 200)
    private String fileName;
}
