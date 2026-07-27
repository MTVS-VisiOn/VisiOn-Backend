package mtvs.onvision.vision.favorite.repository;

import mtvs.onvision.vision.favorite.domain.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    Boolean existsByUserIdAndPkeyAndDeletedAtIsNull(Long userId, String pKey);
    List<Favorite> findAllByUserIdAndDeletedAtIsNull(Long userId);
    @Query("""
        select f from Favorite f
        where f.user.id = :userId
            and f.deletedAt is null
            and (f.nickname like %:keyword% or f.name like %:keyword%)
            order by case when f.nickname like %:keyword% then 0 else 1 end, f.name
    """)
    List<Favorite> searchFavorite(Long userId, String keyword);

    Optional<Favorite> findByIdAndUserIdAndDeletedAtIsNull(Long userId, Long favoriteId);
}
