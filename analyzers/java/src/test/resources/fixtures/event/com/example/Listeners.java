package com.example;

import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionalEventListener;

public class Listeners {

    static final String ACTIVE_CONDITION = "#event != null";

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

    @EventListener(condition = Listeners.ACTIVE_CONDITION)
    void onOrderWhenConstantCondition(OrderEvent event) {
    }

    @EventListener
    void onSpecial(SpecialOrderEvent event) {
    }
}
