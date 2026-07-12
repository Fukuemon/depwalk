package com.example.lib;

/**
 * Minimal "external library" class for the E2E fixture's classpath scenario.
 *
 * This source is kept for reproducibility / audit only; the E2E fixture uses
 * the pre-compiled {@code ../fixture-lib.jar} (see ../README.md for the
 * rebuild command). The fixture project's WidgetUsingLib extends this class
 * without overriding helper(), so a call to helper() must be lifted to the
 * scope-internal subtype (attribution rule: declaring site out of scope,
 * receiver's static type in scope).
 */
public class LibBase {

    public String helper() {
        return "lib-helper";
    }
}
