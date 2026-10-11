package com.rahbar.website;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A member of the worldwide directory. Visitors sign up (not approved); staff approve. Contact details are public only with showContact. */
@Entity
@Table(name = "web_members")
@Getter @Setter
public class WebMember extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public enum HomeState { BIHAR, JHARKHAND, OTHER }

    @Column(nullable = false)
    private String fullName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HomeState homeState = HomeState.BIHAR;
    private String homeDistrict;
    @Column(nullable = false)
    private String currentCountry;
    private String currentCity;
    private String profession;
    private String organization;
    @Column(nullable = false, unique = true)
    private String email;
    private String phone;
    private Long chapterId;
    private boolean showContact;
    private boolean approved;
}
