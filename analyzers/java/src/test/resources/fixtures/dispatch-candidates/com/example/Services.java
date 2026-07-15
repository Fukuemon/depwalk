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

class BaseProcessor {
    void process() {
    }
}

class OverridingProcessor extends BaseProcessor {
    @Override
    void process() {
    }

    void callSuper() {
        super.process();
    }

    Runnable referenceSuper() {
        return super::process;
    }
}

interface ParentDispatchService {
    void invoke();
}

interface ChildDispatchService extends ParentDispatchService {
}

class ChildDispatchImplementation implements ChildDispatchService {
    public void invoke() {
    }
}

interface IntersectionDispatchA {
    void invoke();
}

interface IntersectionDispatchB {
}

class IntersectionDispatchAOnly implements IntersectionDispatchA {
    @Override
    public void invoke() {
    }
}

class IntersectionDispatchBoth implements IntersectionDispatchA, IntersectionDispatchB {
    @Override
    public void invoke() {
    }
}

class ParentOnlyDispatchImplementation implements ParentDispatchService {
    public void invoke() {
    }
}

interface NonBeanService {
    void execute();
}

class NonBeanOne implements NonBeanService {
    public void execute() {
    }
}

class NonBeanTwo implements NonBeanService {
    public void execute() {
    }
}
