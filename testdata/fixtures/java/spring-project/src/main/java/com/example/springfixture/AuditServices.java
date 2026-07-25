package com.example.springfixture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

interface AuditService {
    void audit();
}

@Service
class FileAuditService implements AuditService {
    public void audit() {
    }
}

@Service
class DatabaseAuditService implements AuditService {
    public void audit() {
    }
}

@Component
class AuditRunner {
    @Autowired
    private AuditService auditService;

    void run() {
        auditService.audit();
    }
}
