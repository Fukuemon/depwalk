package com.example;

import jakarta.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;

class Jobs {

    @PostConstruct
    void init() {
        helper();
    }

    @PreDestroy
    void shutdown() {
    }

    @Scheduled(cron = "0 0 * * * *")
    void nightly() {
        helper();
    }

    void helper() {
    }
}
