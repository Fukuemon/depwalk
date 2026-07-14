package com.example;

interface SpringDataRepo extends org.springframework.data.repository.Repository<Object, Long> {
}

@org.apache.ibatis.annotations.Mapper
interface MyBatisRepo {
}

interface PlainRepo {
}

class RuntimeConsumer {
    @org.springframework.beans.factory.annotation.Autowired
    SpringDataRepo springDataRepo;

    @org.springframework.beans.factory.annotation.Autowired
    MyBatisRepo myBatisRepo;

    @org.springframework.beans.factory.annotation.Autowired
    PlainRepo plainRepo;
}
