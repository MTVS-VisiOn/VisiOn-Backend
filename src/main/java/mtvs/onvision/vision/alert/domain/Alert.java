package mtvs.onvision.vision.alert.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import mtvs.onvision.vision.common.domain.BaseEntity;
import mtvs.onvision.vision.user.domain.User;

import java.time.Instant;

@Getter
@Entity
@Table(name = "alerts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Alert extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AlertType type;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false, length = 100)
    private String address;

    @Column(nullable = false, length = 100)
    private String s3Key;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private String action;

    @ManyToOne(fetch = FetchType.LAZY)
    private User sender;

    public Alert(AlertType type, String content, Double latitude, Double longitude, String address, String s3Key, Instant occurredAt, String action, User sender) {
        this.type = type;
        this.content = content;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.s3Key = s3Key;
        this.occurredAt = occurredAt;
        this.action = action;
        this.sender = sender;
    }
}
