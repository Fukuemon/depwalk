package com.example;

import org.springframework.context.ApplicationEventPublisher;

public class UnresolvedPublisher {

    private final ApplicationEventPublisher publisher;
    private com.missing.UnknownEvent pending;

    UnresolvedPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    void publishPending() {
        publisher.publishEvent(pending);
    }
}
