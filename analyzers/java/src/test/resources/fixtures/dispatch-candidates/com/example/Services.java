package com.example;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

interface PaymentService {
    void pay();
}

@Service("stripe")
class StripePayment implements PaymentService {
    public void pay() {
    }
}

@Service
@Primary
class PaypalPayment implements PaymentService {
    public void pay() {
    }
}

interface AuditService {
    void audit();
}

@Service
class AuditOne implements AuditService {
    public void audit() {
    }
}

@Service
class AuditTwo implements AuditService {
    public void audit() {
    }
}

interface NotificationService {
    void notifyUser();
}

@Service
@Profile("prod")
class ConditionalNotifier implements NotificationService {
    public void notifyUser() {
    }
}
