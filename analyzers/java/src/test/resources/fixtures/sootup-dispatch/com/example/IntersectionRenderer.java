package com.example;

interface IntersectionRendererA {
    void render();
}

interface IntersectionRendererB {
}

final class IntersectionRendererBoth implements IntersectionRendererA, IntersectionRendererB {
    @Override
    public void render() {
    }
}

class SharedRendererBase {
    public void render() {
    }
}

final class SharedRendererAOnly extends SharedRendererBase implements IntersectionRendererA {
}

final class SharedRendererBOnly extends SharedRendererBase implements IntersectionRendererB {
}
