package com.example;

import org.springframework.context.event.EventListener;

class Listener {

    @EventListener
    void onEvent() {
    }

    void invokeDirectly() {
        onEvent();
    }
}
