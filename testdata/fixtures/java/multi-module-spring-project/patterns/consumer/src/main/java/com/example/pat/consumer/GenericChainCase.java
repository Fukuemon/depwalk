package com.example.pat.consumer;

import java.util.function.ToDoubleFunction;

/**
 * パターン①: lambda / generic を含む builder 風 fluent chain。
 * 自己境界 generic builder + overload された生成メソッド + lambda 引数の
 * 型推論で chain の途中から receiver 型が失われる形
 * (実測の metrics builder 系 API を一般化)。
 */
public class GenericChainCase {

    interface Meter {
        String getId();
    }

    static class Builder<T, B extends Builder<T, B>> {
        @SuppressWarnings("unchecked")
        B description(String description) {
            return (B) this;
        }

        @SuppressWarnings("unchecked")
        B tag(String key, String value) {
            return (B) this;
        }

        Meter register() {
            return () -> "meter-id";
        }
    }

    static final class GaugeBuilder<T> extends Builder<T, GaugeBuilder<T>> {
    }

    static <T> GaugeBuilder<T> builder(String name, T source, ToDoubleFunction<T> fn) {
        return new GaugeBuilder<>();
    }

    static GaugeBuilder<Object> builder(String name, java.util.concurrent.Callable<Double> supplier) {
        return new GaugeBuilder<>();
    }

    String run() {
        return builder("m", new Object(), source -> 1.0)
                .description("d")
                .tag("k", "v")
                .register()
                .getId();
    }
}
