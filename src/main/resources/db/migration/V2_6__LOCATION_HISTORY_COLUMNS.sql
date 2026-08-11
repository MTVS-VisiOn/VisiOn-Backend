-- 위치 이력은 append-only다. 수정도 소프트 삭제도 하지 않으므로 두 컬럼을 뺀다.
-- 전송 간격이 3초라 1명당 하루 3만 건 가까이 쌓이는 테이블이고, 안 쓰는 timestamp 2개가 그대로 비용이 된다.
ALTER TABLE location_histories
    DROP COLUMN updated_at,
    DROP COLUMN deleted_at;

-- 위치를 보내는 주체는 피보호자뿐이라 ward_id다(routes.ward_id와 같은 규칙).
ALTER TABLE location_histories
    ADD COLUMN ward_id     BIGINT                      NOT NULL,
    ADD COLUMN latitude    DOUBLE PRECISION            NOT NULL,
    ADD COLUMN longitude   DOUBLE PRECISION            NOT NULL,
    ADD COLUMN accuracy    REAL,
    ADD COLUMN status      VARCHAR(20)                 NOT NULL,
    ADD COLUMN recorded_at TIMESTAMP WITHOUT TIME ZONE NOT NULL;

ALTER TABLE location_histories
    ADD CONSTRAINT fk_location_histories_ward FOREIGN KEY (ward_id) REFERENCES users (id);

-- 재시도 시 중복을 DB에서 흡수한다. INSERT의 ON CONFLICT가 이 인덱스를 판정 기준으로 쓰므로,
-- 이게 없으면 구문 자체가 실패한다.
-- 전송 간격이 최소 3초라 서로 다른 측정이 같은 초에 겹치지 않는다는 전제다.
-- "이 피보호자의 이 기간" 조회 인덱스도 겸한다.
CREATE UNIQUE INDEX uk_location_histories_ward_recorded
    ON location_histories (ward_id, recorded_at);
