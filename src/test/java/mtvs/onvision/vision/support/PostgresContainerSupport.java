package mtvs.onvision.vision.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * DB를 실제로 쓰는 테스트의 공통 기반.
 * <p>
 * 컨테이너를 static으로 한 번만 띄워 JVM 안의 모든 테스트가 공유한다.
 * 클래스마다 새로 띄우면 기동 비용이 클래스 수만큼 곱해진다.
 * JUnit 확장(@Testcontainers) 없이 직접 start()를 호출하므로
 * org.testcontainers:junit-jupiter 의존성이 필요 없고,
 * JVM 종료 시 Ryuk 컨테이너가 정리하므로 stop()도 필요 없다.
 * <p>
 * 운영과 같은 PostgreSQL 18을 쓴다. docker-compose.yml의 이미지 버전과 맞춰야 한다.
 */
public abstract class PostgresContainerSupport {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }
}
