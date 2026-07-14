package com.example.springfixture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

interface NotificationService {
    void notifyUser();
}

@Service
@Profile("prod")
class ProfileNotificationService implements NotificationService {
    public void notifyUser() {
    }
}

@Service
@ConditionalOnProperty(name = "fixture.notification.enabled", havingValue = "true")
class PropertyNotificationService implements NotificationService {
    public void notifyUser() {
    }
}

@Component
class NotificationRunner {
    @Autowired
    private NotificationService notificationService;

    void run() {
        notificationService.notifyUser();
    }
}
