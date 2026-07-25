package com.example;

import java.util.function.Supplier;

/**
 * The call to helperMessage() happens inside a lambda body. The Analyzer
 * keeps the enclosing method (run()) as the callEdge's caller but tags the
 * edge with callEdge.metadata.viaLambda = true.
 */
public class LambdaUser {

    public String run() {
        Supplier<String> supplier = () -> helperMessage();
        return supplier.get();
    }

    private String helperMessage() {
        return "lambda message";
    }
}
