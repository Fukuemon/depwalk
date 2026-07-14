package com.example;

class ConstructorConsumer {
    ConstructorConsumer(NamedService service) {
    }
}

class FieldConsumer {
    @org.springframework.beans.factory.annotation.Autowired
    NamedService service;
}

class SetterConsumer {
    @org.springframework.beans.factory.annotation.Autowired
    void setService(NamedService service) {
    }
}

class SelectionConsumer {
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.beans.factory.annotation.Qualifier("beanQualifier")
    QualifierContract byQualifier;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.beans.factory.annotation.Qualifier("qualifiedByName")
    QualifierContract byBeanName;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.beans.factory.annotation.Qualifier("factoryAlias")
    QualifierContract byAlias;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.beans.factory.annotation.Qualifier("missing")
    QualifierContract qualifierMissing;

    @org.springframework.beans.factory.annotation.Autowired
    PrimaryContract primarySelected;

    @org.springframework.beans.factory.annotation.Autowired
    MultiPrimaryContract multiplePrimary;

    @org.springframework.beans.factory.annotation.Autowired
    PlainContract unspecifiedMultiple;

    @org.springframework.beans.factory.annotation.Autowired
    ConditionalContract conditionalOnly;

    @org.springframework.beans.factory.annotation.Autowired
    ConditionalPrimaryContract conditionalWithPrimary;
}
