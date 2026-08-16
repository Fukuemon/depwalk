package com.example;

class FieldUse {

    private final Action stored = LocalUse::staticHelper;

    void invokeStored() {
        stored.run();
    }

    void invokeStoredViaThis() {
        this.stored.run();
    }
}
