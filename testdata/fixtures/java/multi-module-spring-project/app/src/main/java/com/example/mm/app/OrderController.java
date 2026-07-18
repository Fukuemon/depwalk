package com.example.mm.app;

import com.example.mm.service.OrderService;
import org.springframework.stereotype.Component;

@Component
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    public String placeOrder(String item) {
        return orderService.process(item);
    }
}
