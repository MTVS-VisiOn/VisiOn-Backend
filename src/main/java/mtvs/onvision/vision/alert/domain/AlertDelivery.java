package mtvs.onvision.vision.alert.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import mtvs.onvision.vision.common.domain.BaseEntity;

@Getter
@Entity
@Table(name = "alert_deliveries",
        uniqueConstraints = @UniqueConstraint(name = "uk_alert_delivery", columnNames = {"alert_id", "fid"}),
        indexes = @Index(name = "idx_alert_delivery_status", columnList = "status")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertDelivery extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotifyStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Alert alert;

    @Column(nullable = false)
    private String fid;

    @Column(nullable = false)
    private Integer attemptCount;

    public AlertDelivery(Alert alert, String fid) {
        this.alert = alert;
        this.fid = fid;
        status = NotifyStatus.PENDING;
        attemptCount = 0;
    }

    public void markResult(NotifyStatus status) {
        this.status = status;
        this.attemptCount++;
    }

    public void expire() {
        this.status = NotifyStatus.EXPIRED;
    }
}
