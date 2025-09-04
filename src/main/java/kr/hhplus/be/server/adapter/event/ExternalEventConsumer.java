package kr.hhplus.be.server.adapter.event;

import kr.hhplus.be.server.domain.port.storage.EventLogRepositoryPort;
import kr.hhplus.be.server.domain.entity.EventLog;
import kr.hhplus.be.server.domain.enums.EventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * 외부 데이터 플랫폼 연동용 이벤트 Consumer
 * 
 * external-events 토픽에서 이벤트를 수신하여
 * 외부 시스템으로 데이터를 전송하고 EventLog 상태를 업데이트합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalEventConsumer {

    private final EventLogRepositoryPort eventLogRepository;

    @KafkaListener(
        topics = "external-events",
        groupId = "external-sync-group",
        containerFactory = "externalEventKafkaListenerContainerFactory"
    )
    public void handleExternalEvent(
            ConsumerRecord<String, Object> record,
            Acknowledgment ack) {
        
        String eventKey = record.key();
        Object eventValue = record.value();
        
        log.info("외부 이벤트 수신: partition={}, offset={}, key={}, event={}", 
                record.partition(), record.offset(), eventKey, eventValue.getClass().getSimpleName());
        
        try {
            // 외부 시스템으로 이벤트 전송
            processExternalEvent(eventValue);
            
            // EventLog 상태 업데이트
            updateEventLogStatus(eventKey, EventStatus.COMPLETED, null);
            
            // 수동 커밋
            ack.acknowledge();
            
            log.info("외부 이벤트 처리 완료: key={}", eventKey);
            
        } catch (Exception e) {
            log.error("외부 이벤트 처리 실패: key={}, event={}", eventKey, eventValue, e);
            
            // 실패 상태 업데이트
            updateEventLogStatus(eventKey, EventStatus.FAILED, e.getMessage());
            
            // 에러 상황에서도 ACK (DLQ로 전송되도록)
            ack.acknowledge();
        }
    }

    /**
     * 외부 시스템으로 이벤트 전송 (시뮬레이션)
     */
    private void processExternalEvent(Object event) {
        log.debug("외부 API 호출 시뮬레이션: {}", event.getClass().getSimpleName());
        
        // 처리 시간 시뮬레이션
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("외부 API 호출 중단됨", e);
        }
        
        // 90% 성공률 시뮬레이션
        if (Math.random() < 0.1) {
            throw new RuntimeException("외부 API 호출 실패");
        }
    }

    /**
     * EventLog 상태 업데이트
     * 
     * correlationId 기반으로 정확한 EventLog를 찾아서 업데이트합니다.
     * 동시성 환경에서 잘못된 EventLog가 업데이트되는 것을 방지합니다.
     */
    private void updateEventLogStatus(String key, EventStatus status, String errorMessage) {
        try {
            // key에서 correlationId 추출 (key 형식: "event:correlationId" 또는 correlationId)
            String correlationId = extractCorrelationId(key);
            
            // correlationId와 status가 일치하는 EventLog 찾기
            eventLogRepository.findTopByCorrelationIdAndStatusOrderByCreatedAtDesc(correlationId, EventStatus.PUBLISHED)
                .ifPresentOrElse(
                    eventLog -> {
                        eventLog.updateStatus(status);
                        if (errorMessage != null) {
                            eventLog.setErrorMessage(errorMessage);
                        }
                        eventLogRepository.save(eventLog);
                        
                        log.debug("EventLog 상태 업데이트 완료: correlationId={}, id={}, status={}", 
                                correlationId, eventLog.getId(), status);
                    },
                    () -> log.warn("업데이트할 EventLog를 찾을 수 없음: correlationId={}, key={}", correlationId, key)
                );
                
        } catch (Exception e) {
            log.error("EventLog 상태 업데이트 실패: key={}, status={}", key, status, e);
            // EventLog 업데이트 실패 시도 업무 처리는 진행
        }
    }
    
    /**
     * Event key에서 correlationId를 추출합니다.
     * 
     * Key 형식:
     * - "event:correlationId" → correlationId
     * - "correlationId" → correlationId (그대로)
     */
    private String extractCorrelationId(String key) {
        if (key == null) {
            return null;
        }
        
        // "event:" 접두사가 있는 경우 제거
        if (key.startsWith("event:")) {
            return key.substring("event:".length());
        }
        
        // 접두사가 없으면 key 자체가 correlationId
        return key;
    }
}