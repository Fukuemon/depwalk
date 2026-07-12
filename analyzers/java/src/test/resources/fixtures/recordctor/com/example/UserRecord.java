package com.example;

public class UserRecord {

    public record User(String name, int age) {
        User {
            validate(name);
        }

        private static void validate(String name) {
        }

        public String greet() {
            return "hi " + name();
        }
    }

    public record Point(int x, int y) {
    }

    public void constructUser() {
        new User("alice", 30);
    }

    public void constructPoint() {
        new Point(1, 2);
    }

    public void invokeAccessor(User user) {
        user.name();
    }

    public void invokeGreet(User user) {
        user.greet();
    }
}
