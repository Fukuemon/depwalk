package com.example;

import com.example.lib.LibBase;

/**
 * Extends a jar-declared class (com.example.lib.LibBase, provided via
 * analysisRequest.metadata.classpath) without overriding helper(). The call
 * must be lifted to this scope-internal subtype: calleeMethodId attributes
 * to WidgetUsingLib#helper(), with methodSymbol.metadata.declaringType =
 * com.example.lib.LibBase and metadata.inherited = true.
 */
public class WidgetUsingLib extends LibBase {

    public String useHelper() {
        return helper();
    }
}
