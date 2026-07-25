package com.example;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
class LombokAllArgsConsumer {
    private LombokContract mutableService;
}
