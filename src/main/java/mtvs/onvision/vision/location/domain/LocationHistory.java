package mtvs.onvision.vision.location.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import mtvs.onvision.vision.common.domain.BaseEntity;

@Entity
@Getter
@Table(name = "location_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationHistory extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;



}
