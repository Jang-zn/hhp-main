package kr.hhplus.be.server.adapter.messaging;

import kr.hhplus.be.server.domain.port.messaging.MessagingPort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class InMemoryMessagingAdapter implements MessagingPort {
    
    private final List<Object> publishedEvents = new ArrayList<>();
    
    @Override
    public void publish(String topic, Object event) {
        // TODO: 실제 메시징 로직 구현
        publishedEvents.add(event);
    }
    
    @Override
    public void publishAsync(String topic, Object event) {
        // TODO: 비동기 메시징 로직 구현
        publishedEvents.add(event);
    }
    
    public List<Object> getPublishedEvents() {
        return new ArrayList<>(publishedEvents);
    }
} 