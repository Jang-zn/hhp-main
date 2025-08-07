package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.enums.EventType;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "event_log",
       indexes = {
           @Index(name = "idx_event_log_type", columnList = "eventType"),
           @Index(name = "idx_event_log_status", columnList = "status"),
           @Index(name = "idx_event_log_type_status", columnList = "eventType, status"),
           @Index(name = "idx_event_log_created_at", columnList = "createdAt")
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
} 