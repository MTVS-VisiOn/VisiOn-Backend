package mtvs.onvision.vision.common.init;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.user.domain.Relation;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.repository.RelationRepository;
import mtvs.onvision.vision.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final UserRepository userRepository;
    private final RelationRepository relationRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${init-data.password.ward}")
    private String wardPassword;

    @Value("${init-data.password.guardian}")
    private String guardianPassword;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (userRepository.count() == 0) {
            User ward = new User("test@naver.com", passwordEncoder.encode(wardPassword),"test", "010-0000-0000" , UserRole.WARD);
            userRepository.save(ward);
            User guardian = new User("test1@naver.com", passwordEncoder.encode(guardianPassword), "test", "010-0000-0001", UserRole.GUARDIAN);
            userRepository.save(guardian);
            relationRepository.save(new Relation(ward, guardian));
        }
    }
}
