package com.example;

import java.util.List;

public class Widgets {

    public static class Nested {
        public void inner() {
        }
    }

    static {
        Widgets.staticHelper();
    }

    public Widgets() {
    }

    public Widgets(String name) {
    }

    public void overload(String s) {
    }

    public void overload(int i) {
    }

    public void generics(List<String> values) {
    }

    public void varargs(String... names) {
    }

    public static void staticHelper() {
    }

    public void makeAnonymous() {
        Runnable first = new Runnable() {
            @Override
            public void run() {
            }
        };
        Runnable second = new Runnable() {
            @Override
            public void run() {
            }
        };
        first.run();
        second.run();
    }
}
