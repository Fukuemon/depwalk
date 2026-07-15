package com.example;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

interface NamedService {
}

@Service
class URLService implements NamedService {
}

@Service("explicitService")
class ExplicitService implements NamedService {
}

interface QualifierContract {
}

@Component("qualifiedByName")
class NameQualifiedService implements QualifierContract {
}

@Repository("qualifiedByValue")
@Qualifier("beanQualifier")
class ValueQualifiedService implements QualifierContract {
}

interface PrimaryContract {
}

@Service
class PrimaryDefault implements PrimaryContract {
}

@Service
@Primary
class PrimarySelected implements PrimaryContract {
}

interface MultiPrimaryContract {
}

@Service
@Primary
class MultiPrimaryOne implements MultiPrimaryContract {
}

@Service
@Primary
class MultiPrimaryTwo implements MultiPrimaryContract {
}

interface PlainContract {
}

@Controller
class PlainOne implements PlainContract {
}

@RestController
class PlainTwo implements PlainContract {
}

interface ConditionalContract {
}

@Service
@Profile("prod")
class ConditionalOnly implements ConditionalContract {
}

interface ConditionalPrimaryContract {
}

@Service
@Primary
class ConditionalPrimary implements ConditionalPrimaryContract {
}

@Service
@Profile("test")
class ConditionalAlternative implements ConditionalPrimaryContract {
}

interface ConditionalSelectedPrimaryContract {
}

@Service
@Primary
@Profile("selected")
class ConditionalSelectedPrimary implements ConditionalSelectedPrimaryContract {
}

@Service
class ConditionalSelectedFallback implements ConditionalSelectedPrimaryContract {
}

@Configuration
class FactoryConfig {
    @Bean(name = {"factoryService", "factoryAlias"})
    @Qualifier("factoryQualifier")
    QualifierContract factoryService() {
        return new NameQualifiedService();
    }

    @Bean
    NamedService defaultFactory() {
        return null;
    }

    @Bean("conditionalFactory")
    @ConditionalOnProperty(name = "feature.enabled")
    PlainContract conditionalFactory() {
        return null;
    }

    @Bean
    Supplier<QualifierContract> lambdaFactory() {
        return () -> {
            return new NameQualifiedService();
        };
    }
}

interface ConfigurationConditionalContract {
}

@Configuration
@Profile("config-profile")
class ConditionalFactoryConfig {
    @Bean
    @ConditionalOnProperty(name = "configuration.feature.enabled")
    ConfigurationConditionalContract configurationConditionalFactory() {
        return null;
    }
}
