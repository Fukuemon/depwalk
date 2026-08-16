package com.example;

class Caller {

    String fromLambda() {
        return Retry.retry(() -> work());
    }

    String fromReference() {
        return Retry.retry(Caller::staticWork);
    }

    String work() {
        return "w";
    }

    static String staticWork() {
        return "s";
    }
}
