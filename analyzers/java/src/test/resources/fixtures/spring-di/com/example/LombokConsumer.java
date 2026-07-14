package com.example;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
class LombokConsumer {
    private final LombokContract lombokService;
}
