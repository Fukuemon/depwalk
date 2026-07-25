package com.example.springfixture;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Component;

interface CustomerRepository extends Repository<Customer, Long> {
    Customer findByExternalId(String externalId);
}

@Mapper
interface CustomerMapper {
    Customer findById(long id);
}

record Customer(long id, String externalId) {
}

@Component
class RuntimeRepositoryCaller {
    @Autowired
    private CustomerRepository repository;

    @Autowired
    private CustomerMapper mapper;

    Customer load(String externalId, long id) {
        repository.findByExternalId(externalId);
        return mapper.findById(id);
    }
}
