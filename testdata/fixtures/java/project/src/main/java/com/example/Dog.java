package com.example;

/** Overrides Animal.sound(): calls to Dog#sound() must attribute to Dog itself. */
public class Dog extends Animal {

    @Override
    public String sound() {
        return "Woof";
    }
}
