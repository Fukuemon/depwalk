package com.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class Finder {
    @Autowired
    private Repo repo;

    String use() {
        return repo.find();
    }
}
