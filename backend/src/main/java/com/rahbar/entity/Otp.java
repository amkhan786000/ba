package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "otp")
@IdClass(Otp.OtpId.class)
@Getter @Setter
public class Otp extends Modifiable {
    @Id
    @Column(name = "user_id", length = 50)
    private String userId;

    @Id
    @Column(name = "otp", length = 6)
    private String otp;

    @Column(name = "status")
    private Integer status = 0; // 0=Unused, 1=Used, 2=Expired

    public static class OtpId implements java.io.Serializable {
        private String userId;
        private String otp;

        public OtpId() {}
        public OtpId(String userId, String otp) { this.userId = userId; this.otp = otp; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof OtpId)) return false;
            OtpId that = (OtpId) o;
            return java.util.Objects.equals(userId, that.userId) && java.util.Objects.equals(otp, that.otp);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(userId, otp); }
    }
}
