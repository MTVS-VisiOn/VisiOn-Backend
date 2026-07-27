package mtvs.onvision.vision.favorite.repository;

import mtvs.onvision.vision.favorite.domain.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    Boolean existsByUserIdAndPkeyAndDeletedAtIsNull(Long userId, String pKey);
}
