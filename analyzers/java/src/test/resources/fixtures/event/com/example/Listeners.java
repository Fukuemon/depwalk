package com.example;

import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionalEventListener;

public class Listeners {

    @EventListener
    void onOrder(OrderEvent event) {
    }

    @TransactionalEventListener
    void afterCommit(OrderEvent event) {
    }

    @Profile("batch")
    @EventListener
    void onOrderConditionally(OrderEvent event) {
    }

    @EventListener(condition = "#event != null")
    void onOrderWhenExpression(OrderEvent event) {
    }

    @EventListener
    void onSpecial(SpecialOrderEvent event) {
    }
}
