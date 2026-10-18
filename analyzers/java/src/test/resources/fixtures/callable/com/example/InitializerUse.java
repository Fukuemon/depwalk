package com.example;

class InitializerUse {

    {
        Action action = InitializerUse::staticHelper;
        action.run();
    }

    static void staticHelper() {
    }
}
