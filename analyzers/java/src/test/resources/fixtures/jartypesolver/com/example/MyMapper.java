package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;

public class MyMapper extends ObjectMapper {

    public void invoke() {
        this.writeValueAsString("x");
    }
}
