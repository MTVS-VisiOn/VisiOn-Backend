package mtvs.onvision.vision.user.repository;


import mtvs.onvision.vision.user.domain.Fid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FidRepository extends JpaRepository<Fid, Long> {
    Optional<Fid> findByFid(String fid);
    List<Fid> findByUserId(Long userId);
    void deleteByFid(String fid);
}
