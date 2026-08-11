package mtvs.onvision.vision.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.util.PreConditions;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

import static mtvs.onvision.vision.common.util.AppTime.SEOUL;

@MappedSuperclass
@Getter
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    public void delete(){
        PreConditions.check(this.deletedAt != null,ErrorCode.ALREADY_DELETED);
        // createdAt·updatedAt은 DateTimeProvider가 KST로 채운다. 여기만 JVM 시간대면 컬럼끼리 어긋난다
        this.deletedAt = LocalDateTime.now(SEOUL);
    }
}
