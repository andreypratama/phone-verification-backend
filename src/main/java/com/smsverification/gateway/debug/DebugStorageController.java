package com.smsverification.gateway.debug;

import com.smsverification.gateway.storage.InMemoryGatewayStorage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/debug")
public class DebugStorageController {

    private final InMemoryGatewayStorage storage;

    public DebugStorageController(InMemoryGatewayStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/storage")
    public DebugStorageResponse getStorage() {
        List<?> verifications = storage.findAllVerifications();
        List<?> incomingSms = storage.findAllIncomingSms();
        List<?> nonces = storage.findAllNonces();
        return new DebugStorageResponse(
                verifications.size(),
                incomingSms.size(),
                nonces.size(),
                storage.findAllVerifications(),
                storage.findAllIncomingSms(),
                storage.findAllNonces()
        );
    }
}
