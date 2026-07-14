package com.example.springfixture;

import org.springframework.stereotype.Service;

@Service("stripe")
public class StripePayment implements PaymentService {
    @Override
    public void pay() {
    }
}
