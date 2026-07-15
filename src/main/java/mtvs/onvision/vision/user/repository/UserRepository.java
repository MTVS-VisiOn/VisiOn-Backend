package mtvs.onvision.vision.user.repository;

import mtvs.onvision.vision.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Boolean existsByEmail(String email);
    Boolean existsByPhoneNumber(String phoneNumber);
    Optional<User> findByEmail(String email);
}
