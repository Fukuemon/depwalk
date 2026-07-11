package com.example;

/**
 * Spring-style constructor injection through an interface field. The call to
 * greeter.greet(...) must resolve to the declared interface method
 * (Greeter#greet), not to any concrete implementation, and the resulting
 * callEdge must carry callEdge.metadata.dispatch = "interface".
 */
public class GreetingService {

    private final Greeter greeter;

    public GreetingService(Greeter greeter) {
        this.greeter = greeter;
    }

    public String greetUser(String name) {
        return greeter.greet(name);
    }
}
