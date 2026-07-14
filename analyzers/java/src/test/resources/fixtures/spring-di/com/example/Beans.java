package com.example;

import org.springframework.stereotype.Service;

interface NamedService {
}

@Service
class URLService implements NamedService {
}

@org.springframework.stereotype.Service("explicitService")
class ExplicitService implements NamedService {
}

interface QualifierContract {
}

@org.springframework.stereotype.Component("qualifiedByName")
class NameQualifiedService implements QualifierContract {
}

@org.springframework.stereotype.Repository("qualifiedByValue")
@org.springframework.beans.factory.annotation.Qualifier("beanQualifier")
class ValueQualifiedService implements QualifierContract {
}

interface PrimaryContract {
}

@org.springframework.stereotype.Service
class PrimaryDefault implements PrimaryContract {
}

@org.springframework.stereotype.Service
@org.springframework.context.annotation.Primary
class PrimarySelected implements PrimaryContract {
}

interface MultiPrimaryContract {
}

@org.springframework.stereotype.Service
@org.springframework.context.annotation.Primary
class MultiPrimaryOne implements MultiPrimaryContract {
}

@org.springframework.stereotype.Service
@org.springframework.context.annotation.Primary
class MultiPrimaryTwo implements MultiPrimaryContract {
}

interface PlainContract {
}

@org.springframework.stereotype.Controller
class PlainOne implements PlainContract {
}

@org.springframework.web.bind.annotation.RestController
class PlainTwo implements PlainContract {
}

interface ConditionalContract {
}

@org.springframework.stereotype.Service
@org.springframework.context.annotation.Profile("prod")
class ConditionalOnly implements ConditionalContract {
}

interface ConditionalPrimaryContract {
}

@org.springframework.stereotype.Service
@org.springframework.context.annotation.Primary
class ConditionalPrimary implements ConditionalPrimaryContract {
}

@org.springframework.stereotype.Service
@org.springframework.context.annotation.Profile("test")
class ConditionalAlternative implements ConditionalPrimaryContract {
}

@org.springframework.context.annotation.Configuration
class FactoryConfig {
    @org.springframework.context.annotation.Bean(name = {"factoryService", "factoryAlias"})
    @org.springframework.beans.factory.annotation.Qualifier("factoryQualifier")
    QualifierContract factoryService() {
        return new NameQualifiedService();
    }

    @org.springframework.context.annotation.Bean
    NamedService defaultFactory() {
        return null;
    }

    @org.springframework.context.annotation.Bean("conditionalFactory")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "feature.enabled")
    PlainContract conditionalFactory() {
        return null;
    }

    @org.springframework.context.annotation.Bean
    java.util.function.Supplier<QualifierContract> lambdaFactory() {
        return () -> {
            return new NameQualifiedService();
        };
    }
}
