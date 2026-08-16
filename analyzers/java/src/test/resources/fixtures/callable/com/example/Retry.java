package com.example;

import java.util.function.Supplier;

final class Retry {

    static String retry(Supplier<String> supplier) {
        return supplier.get();
    }
}
