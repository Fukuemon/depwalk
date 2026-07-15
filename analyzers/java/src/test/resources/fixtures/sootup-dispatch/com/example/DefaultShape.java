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

interface UnimplementedAbstractChildDefaultShape extends DefaultShape {
    @Override
    void draw();
}

final class AbstractChildDefaultShapeImpl implements AbstractChildDefaultShape {
    @Override
    public void draw() {
    }
}
