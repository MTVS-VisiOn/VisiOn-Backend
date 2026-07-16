package mtvs.onvision.vision.common.config;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@RequiredArgsConstructor
@Profile("!test")
public class DataInitConfig {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    @Transactional
    CommandLineRunner init() {
        return _ -> {
            User ward = new User("test@naver.com", passwordEncoder.encode("test1234"),"test", "010-0000-0000" );
            userRepository.save(ward);
            userRepository.save(new User("test1@naver.com", passwordEncoder.encode("test1234"),"test", "010-0000-0001",ward));
        };
    }


}
