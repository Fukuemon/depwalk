package com.example;

class PlainInterfaceUse {

    private final NonFunctional service;

    PlainInterfaceUse(NonFunctional service) {
        this.service = service;
    }

    void call() {
        service.first();
    }
}
