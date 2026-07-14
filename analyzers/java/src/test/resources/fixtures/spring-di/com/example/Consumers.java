package com.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
class ConstructorConsumer {
    ConstructorConsumer(NamedService service) {
    }
}

@Component
class FieldConsumer {
    @Autowired
    NamedService service;
}

@Component
class SetterConsumer {
    @Autowired
    void setService(NamedService service) {
    }
}

@Component
class SelectionConsumer {
    @Autowired
    @Qualifier("beanQualifier")
    QualifierContract byQualifier;

    @Autowired
    @Qualifier("qualifiedByName")
    QualifierContract byBeanName;

    @Autowired
    @Qualifier("factoryAlias")
    QualifierContract byAlias;

    @Autowired
    @Qualifier("missing")
    QualifierContract qualifierMissing;

    @Autowired
    PrimaryContract primarySelected;

    @Autowired
    MultiPrimaryContract multiplePrimary;

    @Autowired
    PlainContract unspecifiedMultiple;

    @Autowired
    ConditionalContract conditionalOnly;

    @Autowired
    ConditionalPrimaryContract conditionalWithPrimary;
}
