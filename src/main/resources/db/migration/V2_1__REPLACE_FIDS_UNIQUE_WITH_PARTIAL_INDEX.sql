-- fids.fid의 전역 UNIQUE를 활성 행 한정 부분 인덱스로 교체한다.
-- 소프트 삭제된 행이 남아 있으면 같은 기기를 다시 등록할 수 없기 때문이다.
ALTER TABLE fids
DROP CONSTRAINT uc_fids_fid;

CREATE UNIQUE INDEX uix_fids_fid_active
    ON fids (fid) WHERE deleted_at IS NULL;