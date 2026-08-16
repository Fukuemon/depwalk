package com.example;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

class Api {

    @GetMapping("/items")
    String list() {
        return "items";
    }

    @ExceptionHandler
    String onError() {
        return "error";
    }

    @ModelAttribute
    String common() {
        return "common";
    }

    String plain() {
        return "plain";
    }
}
