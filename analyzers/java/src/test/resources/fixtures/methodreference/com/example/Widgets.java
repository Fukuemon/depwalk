package com.example;

import java.util.function.Function;
import java.util.function.Supplier;

public class Widgets {

    public static class Widget {
        public Widget() {
        }

        public Widget(int size) {
        }

        public String label() {
            return "widget";
        }

        public static String describe() {
            return "static widget";
        }
    }

    public void invokeInstanceReference(Widget widget) {
        Function<Widget, String> fn = Widget::label;
        fn.apply(widget);
    }

    public void invokeStaticReference() {
        Supplier<String> sp = Widget::describe;
        sp.get();
    }

    public void invokeThisReference() {
        Supplier<String> sp = this::toDto;
        sp.get();
    }

    public void invokeConstructorReference() {
        Supplier<Widget> sp = Widget::new;
        sp.get();
    }

    public void invokeConstructorReferenceWithArg() {
        Function<Integer, Widget> fn = Widget::new;
        fn.apply(1);
    }

    public void invokeScopeExternalReference() {
        Supplier<String> sp = java.util.UUID.randomUUID()::toString;
        sp.get();
    }

    public String toDto() {
        return "dto";
    }
}
