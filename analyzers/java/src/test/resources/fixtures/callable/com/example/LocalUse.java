package com.example;

class LocalUse {

    void useLocalLambda() {
        Action action = () -> helper();
        action.run();
    }

    void useLocalReference() {
        Action action = LocalUse::staticHelper;
        action.run();
    }

    void useReassigned() {
        Action action = () -> helper();
        action = () -> staticHelper();
        action.run();
    }

    void helper() {
    }

    static void staticHelper() {
    }
}
