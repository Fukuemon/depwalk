package com.example;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
class PrimaryConsumer {
    @Autowired
    PaymentService payment;

    void checkout() {
        payment.pay();
    }

    void checkoutWithShadowedParameter(PaymentService payment) {
        payment.pay();
    }
}

@Component
class QualifierConsumer {
    @Autowired
    @Qualifier("stripe")
    PaymentService payment;

    void checkout() {
        payment.pay();
    }
}

@Component
class AmbiguousConsumer {
    @Autowired
    AuditService auditService;

    void runAudit() {
        auditService.audit();
    }
}

@Component
class ConditionalConsumer {
    @Autowired
    NotificationService notificationService;

    void notifyCustomer() {
        notificationService.notifyUser();
    }
}

@Component
@RequiredArgsConstructor
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

    void runChild(ChildDispatchService service) {
        service.invoke();
    }
}

@Component
class RenamedConstructorConsumer {
    private final PaymentService payment;

    RenamedConstructorConsumer(PaymentService injectedService) {
        this.payment = injectedService;
    }

    void checkout() {
        this.payment.pay();
    }
}

class PaymentHolder {
    PaymentService payment;
}

@Component
class ForeignFieldConsumer {
    @Autowired
    PaymentService payment;

    void checkoutOther(PaymentHolder other) {
        other.payment.pay();
    }
}

@Component
class SameNamedSetterParametersConsumer {
    @Autowired
    void setPayment(PaymentService service) {
        service.pay();
    }

    @Autowired
    void setAudit(AuditService service) {
        service.audit();
    }
}
