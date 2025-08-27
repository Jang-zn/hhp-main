package kr.hhplus.be.server.infrastructure.messaging.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class EventMessage {
    private Long eventId;
    private String correlationId;
    private String eventType;
    private Object payload;
    private LocalDateTime timestamp;
}