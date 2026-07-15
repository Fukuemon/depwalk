package com.example;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.Repository;

interface SpringDataRepo extends Repository<Object, Long> {
}

@Mapper
interface MyBatisRepo {
}

interface PlainRepo {
}

class RuntimeConsumer {
    @Autowired
    SpringDataRepo springDataRepo;

    @Autowired
    MyBatisRepo myBatisRepo;

    @Autowired
    PlainRepo plainRepo;
}
