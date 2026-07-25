package com.example;

public class Shapes {

    public interface Shape {
        double area();
    }

    public abstract static class AbstractAnimal {
        public abstract String sound();
    }

    public static class Circle implements Shape {
        @Override
        public double area() {
            return 0;
        }
    }

    public void callStatic() {
        Utils.staticOp();
    }

    public void callInterface(Shape shape) {
        shape.area();
    }

    public void callAbstract(AbstractAnimal animal) {
        animal.sound();
    }

    public void callVirtual(Circle circle) {
        circle.area();
    }

    public static class Utils {
        public static void staticOp() {
        }
    }
}
