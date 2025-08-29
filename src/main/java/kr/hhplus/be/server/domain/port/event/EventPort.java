package kr.hhplus.be.server.domain.port.event;

public interface EventPort {
    void publish(String topic, Object event);
} 