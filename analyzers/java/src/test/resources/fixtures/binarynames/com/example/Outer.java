package com.example;

public class Outer {

    public void first() {
        Runnable a = new Runnable() {
            @Override
            public void run() {
                helper();
            }
        };
        a.run();
    }

    public static class Nested {
        public void inNested() {
            Runnable b = new Runnable() {
                @Override
                public void run() {
                }
            };
            b.run();
        }
    }

    public void second() {
        Runnable c = new Runnable() {
            @Override
            public void run() {
            }
        };
        class Local {
            void go() {
                helper();
            }
        }
        new Local().go();
        c.run();
    }

    public void third() {
        class Local {
            void go() {
            }
        }
        new Local().go();
    }

    void helper() {
    }
}
