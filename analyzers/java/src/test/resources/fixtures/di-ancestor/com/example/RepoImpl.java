package com.example;

import org.springframework.stereotype.Component;

@Component
public class RepoImpl extends com.missing.ExternalBase implements Repo {
    public String find() {
        return "x";
    }
}
