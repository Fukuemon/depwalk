package com.example;

/** Concrete Greeter implementation, invoked both through the interface and directly. */
public class EnglishGreeter implements Greeter {

    @Override
    public String greet(String name) {
        return "Hello, " + name;
    }
}
