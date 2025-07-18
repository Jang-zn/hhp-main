package kr.hhplus.be.server.adapter.messaging;

import kr.hhplus.be.server.domain.port.messaging.MessagingPort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class InMemoryMessagingAdapter implements MessagingPort {
    
    private final List<Object> publishedEvents = new ArrayList<>();
    
    /**
     * Stores the given event in memory as a published message for the specified topic.
     *
     * @param topic the topic to which the event is published
     * @param event the event object to be stored
     */
    @Override
    public void publish(String topic, Object event) {
        // TODO: 실제 메시징 로직 구현
        publishedEvents.add(event);
    }
    
    /**
     * Stores the given event in memory for the specified topic, simulating asynchronous message publishing.
     *
     * @param topic the topic to which the event would be published
     * @param event the event object to store
     */
    @Override
    public void publishAsync(String topic, Object event) {
        // TODO: 비동기 메시징 로직 구현
        publishedEvents.add(event);
    }
    
    /**
     * Returns a list containing all events that have been published in memory.
     *
     * @return a new list of published event objects
     */
    public List<Object> getPublishedEvents() {
        return new ArrayList<>(publishedEvents);
    }
} 