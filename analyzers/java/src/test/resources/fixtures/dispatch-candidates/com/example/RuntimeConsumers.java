package com.example;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Component;

interface UserRepository extends Repository<Object, Long> {
    void find();
}

@Mapper
interface UserMapper {
    void map();
}

interface MissingService {
    void missing();
}

@Component
class RuntimeConsumer {
    @Autowired
    UserRepository repository;

    @Autowired
    UserMapper mapper;

    @Autowired
    MissingService missingService;

    void invokeRuntimeTypes() {
        repository.find();
        mapper.map();
        missingService.missing();
    }
}
