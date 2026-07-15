package com.example;

public interface DefaultShape {
    default void draw() {
    }
}

interface ChildDefaultShape extends DefaultShape {
}

interface AbstractChildDefaultShape extends DefaultShape {
    @Override
    void draw();
}
