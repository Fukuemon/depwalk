package com.example.mm.repository;

import org.springframework.stereotype.Repository;

@Repository
public class JpaOrderRepository implements OrderRepository {

    @Override
    public String save(String item) {
        return "saved:" + item;
    }
}
