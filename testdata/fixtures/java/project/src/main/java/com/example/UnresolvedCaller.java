package com.example;

/**
 * triggerUnresolved() calls a method that does not exist on java.lang.Object,
 * which the symbol solver cannot resolve: this must produce a
 * JAVA_UNRESOLVED_SYMBOL diagnostic and no callEdge for that call site.
 * resolvedCall() calls a normally resolvable constructor + method in the
 * same file, and must still produce their callEdges: an unresolved call must
 * not block resolution of the rest of the file.
 */
public class UnresolvedCaller {

    public void triggerUnresolved() {
        Object obj = new Object();
        obj.notAMethodOnObject();
    }

    public String resolvedCall() {
        return new EnglishGreeter().greet("x");
    }
}
