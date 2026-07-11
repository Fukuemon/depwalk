package com.example;

public class UserService extends BaseService {

    @Override
    public void save() {
    }

    public void invokeSave() {
        this.save();
    }

    public void invokeSuperSave() {
        super.save();
    }

    public void invokeNotOverridden() {
        this.notOverridden();
    }

    public void invokeImplicitThis() {
        save();
    }

    public void invokeToString() {
        this.toString();
    }

    public void constructScopeInternal() {
        new UserService();
    }

    public void constructScopeExternal() {
        new com.example.lib.ExternalRepo();
    }
}
