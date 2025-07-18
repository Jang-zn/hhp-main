package kr.hhplus.be.server.domain.entity;

import jakarta.persistence.*;
import kr.hhplus.be.server.domain.enums.EventStatus;
import kr.hhplus.be.server.domain.enums.EventType;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
public class EventLog extends BaseEntity {

    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Lob
    private String payload;

    @Enumerated(EnumType.STRING)
    private EventStatus status;
} 