package com.favo.backend.Domain.user;

import com.favo.backend.Domain.Common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;


//System_user tablosnun gerekli anotasyonlar ile detaylanmış hali

@Entity
@Table(name = "system_user")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(
        name = "dtype",
        discriminatorType = DiscriminatorType.STRING,
        length = 30
)
@Getter
@Setter
public abstract class SystemUser extends BaseEntity {

    @Column(name = "firebase_uid", nullable = false, unique = true, length = 128)
    private String firebaseUid;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "username", nullable = false, length = 50)
    private String userName;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "surname", length = 100)
    private String surname;

    @Column(name = "birthdate")
    private LocalDate birthdate;

    /**
     * null = eski kayıtlar (doğrulanmış sayılır). false = kayıt sonrası kod bekleniyor.
     */
    @Column(name = "email_verified")
    private Boolean emailVerified;

    /**
     * Set when e-posta doğrulaması kalıcı olarak onaylandı (yeni akış: OTP sonrası).
     */
    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    /**
     * true = kullanıcı profilini herkese anonim göstermek istiyor (ayarlardan).
     * null = eski kayıtlar, anonim değil sayılır.
     */
    @Column(name = "profile_anonymous")
    private Boolean profileAnonymous;

    /**
     * Son başarılı "kodu yeniden gönder" zamanı. Kayıttaki ilk mail bu alanı doldurmaz;
     * böylece register sonrası ilk resend 400 RESEND_COOLDOWN vermez.
     */
    @Column(name = "verification_email_last_resend_at")
    private LocalDateTime verificationEmailLastResendAt;

    // isActive -> BaseEntity'den geliyor

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_type_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_system_user_user_type")
    )
    private UserType userType;

    public abstract void deactivate();
}
