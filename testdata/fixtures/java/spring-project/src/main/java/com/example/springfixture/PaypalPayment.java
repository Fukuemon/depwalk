package com.example.springfixture;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class PaypalPayment implements PaymentService {
    @Override
    public void pay() {
    }
}
