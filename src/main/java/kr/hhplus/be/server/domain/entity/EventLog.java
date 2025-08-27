package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.enums.EventType;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "event_log",
       indexes = {
           @Index(name = "idx_event_log_status", columnList = "status"),
           @Index(name = "idx_event_log_correlation", columnList = "correlationId")
       })
public class EventLog extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @NotNull
    private EventType eventType;

    @Lob
    @NotBlank
    private String payload;

    @Enumerated(EnumType.STRING)
    @NotNull
    private EventStatus status;

    // STEP 15 추가 필드들
    private String externalEndpoint; // 전송 대상 엔드포인트

    @Builder.Default
    private int retryCount = 0; // 재시도 횟수

    private String correlationId; // 추적 ID

    private String errorMessage; // 실패 사유

    private LocalDateTime nextRetryAt; // 다음 재시도 시각

    // 상태 전이 메서드들
    public void updateStatus(EventStatus newStatus) {
        this.status = newStatus;
    }

    public void markAsFailed(String errorMessage) {
        this.status = EventStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public void setExternalEndpoint(String endpoint) {
        this.externalEndpoint = endpoint;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
} 