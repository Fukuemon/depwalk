package com.example;

interface PaymentService {
    void pay();
}

@org.springframework.stereotype.Service("stripe")
class StripePayment implements PaymentService {
    public void pay() {
    }
}

@org.springframework.stereotype.Service
@org.springframework.context.annotation.Primary
class PaypalPayment implements PaymentService {
    public void pay() {
    }
}

interface AuditService {
    void audit();
}

@org.springframework.stereotype.Service
class AuditOne implements AuditService {
    public void audit() {
    }
}

@org.springframework.stereotype.Service
class AuditTwo implements AuditService {
    public void audit() {
    }
}

interface NotificationService {
    void notifyUser();
}

@org.springframework.stereotype.Service
@org.springframework.context.annotation.Profile("prod")
class ConditionalNotifier implements NotificationService {
    public void notifyUser() {
    }
}
