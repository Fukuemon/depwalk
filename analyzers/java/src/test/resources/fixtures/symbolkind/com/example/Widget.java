package com.example;

import java.util.List;

public class Widget {

    private int a = computeA();

    {
        computeB();
    }

    public Widget() {
    }

    public Widget(String name) {
    }

    private static int computeA() {
        return 1;
    }

    private static void computeB() {
    }

    public void runWithLambda(List<String> items) {
        Runnable r = () -> {
            helper();
        };
        r.run();
    }

    private void helper() {
    }
}
