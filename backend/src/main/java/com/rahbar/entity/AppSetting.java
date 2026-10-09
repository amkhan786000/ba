package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** A small application setting stored in the database (e.g. the generated push notification keys). */
@Entity
@Table(name = "app_settings")
@Getter @Setter
public class AppSetting {
    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    @Lob
    @Column(name = "setting_value", nullable = false, columnDefinition = "TEXT")
    private String value;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
