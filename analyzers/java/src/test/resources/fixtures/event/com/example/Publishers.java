package com.example;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;

public class Publishers {

    private final ApplicationEventPublisher publisher;
    private final ApplicationContext context;
    private final MyBus bus;

    Publishers(ApplicationEventPublisher publisher, ApplicationContext context, MyBus bus) {
        this.publisher = publisher;
        this.context = context;
        this.bus = bus;
    }

    void publishOrder() {
        publisher.publishEvent(new OrderEvent());
    }

    void publishSpecial() {
        publisher.publishEvent(new SpecialOrderEvent());
    }

    void publishViaContext() {
        context.publishEvent(new OrderEvent());
    }

    void publishViaBus() {
        bus.publishEvent(new OrderEvent());
    }
}
