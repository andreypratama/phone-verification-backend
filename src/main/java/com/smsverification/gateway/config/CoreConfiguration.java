package com.smsverification.gateway.config;

import com.smsverification.gateway.core.crypto.HmacService;
import com.smsverification.gateway.core.phone.PhoneNumberNormalizer;
import com.smsverification.gateway.core.verification.VerificationCodeGenerator;
import com.smsverification.gateway.core.verification.VerificationCodeParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class CoreConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    HmacService hmacService() {
        return new HmacService();
    }

    @Bean
    PhoneNumberNormalizer phoneNumberNormalizer(AppProperties properties) {
        return new PhoneNumberNormalizer(properties.phone().defaultCountryCode());
    }

    @Bean
    VerificationCodeGenerator verificationCodeGenerator(AppProperties properties) {
        return new VerificationCodeGenerator(properties.verification().codeLength(), new SecureRandom());
    }

    @Bean
    VerificationCodeParser verificationCodeParser(AppProperties properties) {
        return new VerificationCodeParser(
                properties.verification().prefix(),
                properties.verification().codeLength()
        );
    }
}
