package mtvs.onvision.vision.location.repository;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.location.dto.LocationReport;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static mtvs.onvision.vision.common.util.AppTime.SEOUL;

/**
 * 위치 이력 배치 INSERT.
 *
 * JPA를 쓰지 않는 이유가 둘이다.
 * 1. `ON CONFLICT DO NOTHING`을 JPQL로 표현할 수 없다. 재시도로 들어온 중복 하나 때문에
 *    배치 전체가 유니크 제약 위반으로 롤백된다
 * 2. `id`가 IDENTITY라 Hibernate가 JDBC 배치를 아예 끈다. 생성된 키를 받아야 해서 건별 INSERT가 나간다
 */
@Repository
@RequiredArgsConstructor
public class LocationHistoryJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT = """
            INSERT INTO location_histories
                (ward_id, latitude, longitude, accuracy, status, recorded_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (ward_id, recorded_at) DO NOTHING
            """;

    /**
     * @return 실제로 삽입된 건수. 충돌로 건너뛴 행은 세지 않으므로
     *         반환값이 입력보다 작으면 재시도로 다시 들어온 데이터가 있었다는 뜻이다
     */
    @Transactional
    public int batchInsert(List<LocationReport> reports) {
        if (reports.isEmpty()) return 0;

        int[] affected = jdbcTemplate.batchUpdate(INSERT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                LocationReport r = reports.get(i);
                ps.setLong(1, r.userId());   // → ward_id. 위치를 보내는 건 피보호자뿐이다
                ps.setDouble(2, r.latitude());
                ps.setDouble(3, r.longitude());
                ps.setObject(4, r.accuracy(), Types.REAL);   // nullable이라 타입을 명시해야 한다
                ps.setString(5, r.status().name());
                // recorded_at은 측정 시각이라 Hibernate가 Instant를 다루는 방식과 같게 UTC 벽시계로 넣는다.
                // created_at은 감사 시각이라 DateTimeProvider와 같게 KST다.
                // 한 행에 두 규칙이 섞인 게 맞다 — 의미가 다른 컬럼이다
                ps.setObject(6, r.recordedAt().atOffset(ZoneOffset.UTC).toLocalDateTime());
                ps.setObject(7, LocalDateTime.now(SEOUL));
            }

            @Override
            public int getBatchSize() {
                return reports.size();
            }
        });
        return Arrays.stream(affected).sum();
    }
}
