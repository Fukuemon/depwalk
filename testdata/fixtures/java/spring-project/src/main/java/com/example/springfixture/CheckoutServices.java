package com.example.springfixture;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class LombokCheckoutService {
    private final PaymentService paymentService;

    void checkout() {
        paymentService.pay();
    }
}

@Component
class FieldCheckoutService {
    @Autowired
    @Qualifier("stripe")
    private PaymentService paymentService;

    void checkout() {
        paymentService.pay();
    }
}

@Component
class SetterCheckoutService {
    private PaymentService paymentService;

    @Autowired
    void setPaymentService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    void checkout() {
        paymentService.pay();
    }
}

@Component
class ConfiguredCheckoutService {
    @Autowired
    @Qualifier("configuredPaymentAlias")
    private PaymentService paymentService;

    void checkout() {
        paymentService.pay();
    }
}
