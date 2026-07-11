package com.example;

/**
 * Exercises the inheritance declaration-site branch (design doc
 * "帰属型の決定規則"): the override case (Dog) resolves to the subclass
 * declaration, the no-override case (Cat) resolves to the superclass
 * declaration.
 */
public class AnimalCaller {

    public String describeDog(Dog dog) {
        return dog.sound();
    }

    public String describeCat(Cat cat) {
        return cat.sound();
    }
}
