package com.example;

interface UserRepository extends org.springframework.data.repository.Repository<Object, Long> {
    void find();
}

@org.apache.ibatis.annotations.Mapper
interface UserMapper {
    void map();
}

interface MissingService {
    void missing();
}

@org.springframework.stereotype.Component
class RuntimeConsumer {
    @org.springframework.beans.factory.annotation.Autowired
    UserRepository repository;

    @org.springframework.beans.factory.annotation.Autowired
    UserMapper mapper;

    @org.springframework.beans.factory.annotation.Autowired
    MissingService missingService;

    void invokeRuntimeTypes() {
        repository.find();
        mapper.map();
        missingService.missing();
    }
}
