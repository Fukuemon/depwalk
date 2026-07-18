package com.example.mm.service;

import com.example.mm.repository.OrderRepository;
import org.springframework.stereotype.Service;

@Service
public class DefaultOrderService implements OrderService {

    private final OrderRepository orderRepository;

    public DefaultOrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public String process(String item) {
        return orderRepository.save(item);
    }
}
