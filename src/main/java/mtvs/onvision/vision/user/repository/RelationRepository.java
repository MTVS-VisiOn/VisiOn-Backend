package mtvs.onvision.vision.user.repository;

import mtvs.onvision.vision.user.domain.Relation;
import mtvs.onvision.vision.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RelationRepository extends JpaRepository<Relation, Long> {
    Boolean existsByWard(User ward);
    Optional<Relation> findByGuardianId(Long guardianId);
    Optional<Relation> findByWardId(Long wardId);
}
