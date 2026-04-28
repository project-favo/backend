package com.favo.backend.Domain.review;

import com.favo.backend.Domain.Common.BaseEntity;
import com.favo.backend.Domain.user.GeneralUser;
import com.favo.backend.Domain.user.SystemUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "review_flags")
@Getter
@Setter
public class ReviewFlag extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "review_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_review_flag_review")
    )
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "reported_by_id",
            foreignKey = @ForeignKey(name = "fk_review_flag_reported_by")
    )
    private GeneralUser reportedBy; // null ise AI tarafından oluşturulmuştur

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 64)
    private FlagReason reason;

    @Column(name = "notes", length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "resolved_by_id",
            foreignKey = @ForeignKey(name = "fk_review_flag_resolved_by")
    )
    private SystemUser resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", length = 32)
    private FlagResolution resolution;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}

