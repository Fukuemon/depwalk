package com.example;

@org.springframework.stereotype.Component
class PrimaryConsumer {
    @org.springframework.beans.factory.annotation.Autowired
    PaymentService payment;

    void checkout() {
        payment.pay();
    }

    void checkoutWithShadowedParameter(PaymentService payment) {
        payment.pay();
    }
}

@org.springframework.stereotype.Component
class QualifierConsumer {
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.beans.factory.annotation.Qualifier("stripe")
    PaymentService payment;

    void checkout() {
        payment.pay();
    }
}

@org.springframework.stereotype.Component
class AmbiguousConsumer {
    @org.springframework.beans.factory.annotation.Autowired
    AuditService auditService;

    void runAudit() {
        auditService.audit();
    }
}

@org.springframework.stereotype.Component
class ConditionalConsumer {
    @org.springframework.beans.factory.annotation.Autowired
    NotificationService notificationService;

    void notifyCustomer() {
        notificationService.notifyUser();
    }
}

@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
class LombokConsumer {
    private final PaymentService payment;

    void checkout() {
        payment.pay();
    }
}

class DispatchOnlyConsumer {
    void run(AuditService service) {
        service.audit();
    }
}
