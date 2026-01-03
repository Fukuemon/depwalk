package com.example;

public class Service {
    private Repository repository;
    private Util util;

    public Service() {
        this.repository = new Repository();
        this.util = new Util();
    }

    public void process(String input) {
        String normalized = util.normalize(input);
        repository.save(normalized);
    }

    public String fetch(String id) {
        return repository.findById(id);
    }
}

