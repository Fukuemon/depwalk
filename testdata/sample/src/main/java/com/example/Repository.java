package com.example;

public class Repository {
    public void save(String data) {
        System.out.println("Saving: " + data);
    }

    public String findById(String id) {
        return "Data for " + id;
    }
}

