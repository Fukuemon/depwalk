package com.example.springfixture;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class FixtureConfiguration {
    @Bean(name = {"configuredPayment", "configuredPaymentAlias"})
    @Qualifier("configured")
    PaymentService configuredPayment() {
        return new StripePayment();
    }
}
