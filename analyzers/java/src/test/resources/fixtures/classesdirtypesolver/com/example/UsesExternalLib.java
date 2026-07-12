package com.example;

import com.example.lib.ExternalLib;

public class UsesExternalLib extends ExternalLib {

    public void invoke() {
        this.ping();
    }
}
