package com.smsverification.gateway.sms;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class IncomingSmsController {

    private final IncomingSmsService incomingSmsService;

    public IncomingSmsController(IncomingSmsService incomingSmsService) {
        this.incomingSmsService = incomingSmsService;
    }

    @PostMapping("/internal/sms/incoming")
    public IncomingSmsResponse receive(@Valid @RequestBody IncomingSmsRequest request) {
        return incomingSmsService.receive(request);
    }

    @GetMapping("/api/v1/verifications/{verificationId}/sms")
    public SmsListResponse list(@PathVariable UUID verificationId) {
        return incomingSmsService.listByVerification(verificationId);
    }
}
