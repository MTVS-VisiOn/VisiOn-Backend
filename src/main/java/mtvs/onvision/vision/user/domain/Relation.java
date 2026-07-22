package mtvs.onvision.vision.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import mtvs.onvision.vision.common.domain.BaseEntity;

@Entity
@Getter
@Table(name = "relations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Relation extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @JoinColumn(nullable = false)
    @OneToOne(fetch = FetchType.LAZY)
    User ward;

    @JoinColumn(nullable = false)
    @OneToOne(fetch = FetchType.LAZY)
    User guardian;

    private boolean offlineAlertEnabled;  // 피보호자 장시간 미접속 알림
    private boolean arrivalAlertEnabled;  // 피보호자 목적지 도착 알림

    public Relation(User ward, User guardian) {
        this.ward = ward;
        this.guardian = guardian;
        this.offlineAlertEnabled = true;
        this.arrivalAlertEnabled = true;
    }
}
