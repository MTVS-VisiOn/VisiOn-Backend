package mtvs.onvision.vision.favorite.repository;

import mtvs.onvision.vision.favorite.domain.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    Boolean existsByUserIdAndPkeyAndDeletedAtIsNull(Long userId, String pKey);
    Page<Favorite> findByUserIdAndDeletedAtIsNullOrderByNicknameAscNameAsc(Long userId, Pageable pageable);
    @Query("""
        select f from Favorite f
        where f.user.id = :userId
            and f.deletedAt is null
            and (f.nickname like :pattern escape '!' or f.name like :pattern escape '!')
            order by case when f.nickname like :pattern escape '!' then 0 else 1 end, f.name
    """)
    Page<Favorite> searchFavorite(Long userId, String pattern, Pageable pageable);

    Optional<Favorite> findByIdAndUserIdAndDeletedAtIsNull(Long favoriteId, Long userId);
}
