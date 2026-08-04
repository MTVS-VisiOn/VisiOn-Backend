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

    public Relation(User ward, User guardian) {
        this.ward = ward;
        this.guardian = guardian;
    }

}
