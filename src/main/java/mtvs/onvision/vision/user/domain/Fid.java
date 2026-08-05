package mtvs.onvision.vision.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import mtvs.onvision.vision.common.domain.BaseEntity;

@Entity
@Getter
@Table(name = "fids")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Fid extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false,  unique = true)
    private String fid;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    public Fid(String fid, User user) {
        this.fid = fid;
        this.user = user;
    }

    public void refresh (User user) {
        this.user = user;
    }
}


