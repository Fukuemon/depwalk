package com.example;

/**
 * Does not override sound(): calls to Cat.sound() must attribute to the
 * declaring superclass Animal, since both Cat and Animal are scope-internal
 * (no lift occurs — lift only applies when the declaring site is out of
 * scope).
 */
public class Cat extends Animal {
}
