package com.rahbar.entity;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** One REB setting (school name, admission number prefix, ...). */
@Entity
@Table(name = "reb_settings")
@Getter @Setter
public class RebSetting extends Modifiable {

    @Id
    @Column(name = "setting_key", length = 60)
    private String key;
    @Column(name = "setting_value", length = 1000)
    private String value;
}
