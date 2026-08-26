package mtvs.onvision.vision.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.LocalDateTime;
import java.util.Optional;

import static mtvs.onvision.vision.common.util.AppTime.SEOUL;

@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@Configuration
public class JpaConfig {

    /**
     * 감사 시각을 KST 벽시계로 고정한다.
     * <p>
     * 기본 제공자(`CurrentDateTimeProvider`)는 `LocalDateTime.now()`라 JVM 기본 시간대를 탄다.
     * 배포 컨테이너에는 TZ 설정이 없어 UTC이고 개발 PC는 KST라, 같은 `createdAt` 컬럼에
     * 의미가 다른 값이 섞인다. 하루 경계로 조회하는 API가 여기에 걸린다.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(LocalDateTime.now(SEOUL));
    }
}
