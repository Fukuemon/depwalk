package com.example;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
class LombokConsumer {
    private final LombokContract lombokService;
}

@Component
@RequiredArgsConstructor
class LombokNonNullConsumer {
    @NonNull
    private LombokContract nonNullService;
    private LombokContract optionalService;
}
