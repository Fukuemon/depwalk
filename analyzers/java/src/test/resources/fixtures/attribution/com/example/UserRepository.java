package com.example;

import com.example.lib.ExternalRepo;

public class UserRepository extends ExternalRepo {

    public void invokeFind() {
        this.find(1L);
    }

    public void invokeExternalDirect(ExternalRepo repo) {
        repo.find(2L);
    }

    public void invokeInheritedStaticUnqualified() {
        staticFind();
    }
}
