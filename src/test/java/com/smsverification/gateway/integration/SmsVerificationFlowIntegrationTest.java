package com.smsverification.gateway.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smsverification.gateway.core.crypto.HmacService;
import com.smsverification.gateway.storage.InMemoryGatewayStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SmsVerificationFlowIntegrationTest {

    private static final String API_SECRET = "test-api-secret-at-least-32-characters";
    private static final String SMS_SECRET = "test-sms-secret-at-least-32-characters";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryGatewayStorage storage;

    private final HmacService hmacService = new HmacService();

    @BeforeEach
    void cleanStorage() {
        storage.clear();
    }

    @Test
    void performsCompleteGenerateReceivePollAndReadSmsFlow() throws Exception {
        String createBody = "{\"phoneNumber\":\"0812 3456-7890\"}";
        SignedHeaders createHeaders = signApi("POST", "/api/v1/verifications", createBody);

        MvcResult createResult = mockMvc.perform(post("/api/v1/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("X-Timestamp", createHeaders.timestamp())
                        .header("X-Nonce", createHeaders.nonce())
                        .header("X-Signature", createHeaders.signature()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phoneNumber").value("+6281234567890"))
                .andExpect(jsonPath("$.destinationNumber").value("0811-0000-9999"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsByteArray());
        String verificationId = created.get("verificationId").asText();
        String code = created.get("code").asText();
        assertThat(code).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}");
        assertThat(created.get("smsText").asText()).isEqualTo("VERIF " + code);

        String smsBody = objectMapper.writeValueAsString(new SmsForwarderPayload(
                "+6281234567890",
                "VERIF " + code,
                Instant.now().minusSeconds(1).toEpochMilli(),
                Instant.now().toEpochMilli(),
                "SIM1"
        ));
        String smsSignature = hmacService.hmacSha256Hex(SMS_SECRET, smsBody.getBytes(StandardCharsets.UTF_8));

        MvcResult incomingResult = mockMvc.perform(post("/internal/sms/incoming")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(smsBody)
                        .header("X-Signature", smsSignature))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.matchStatus").value("MATCHED"))
                .andExpect(jsonPath("$.verificationId").value(verificationId))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andReturn();
        String smsId = objectMapper.readTree(incomingResult.getResponse().getContentAsByteArray())
                .get("smsId")
                .asText();

        mockMvc.perform(post("/internal/sms/incoming")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(smsBody)
                        .header("X-Signature", smsSignature))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsId").value(smsId))
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.matchStatus").value("MATCHED"))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

        String statusPath = "/api/v1/verifications/" + verificationId + "/status";
        SignedHeaders statusHeaders = signApi("GET", statusPath, "");
        mockMvc.perform(get(statusPath)
                        .header("X-Timestamp", statusHeaders.timestamp())
                        .header("X-Nonce", statusHeaders.nonce())
                        .header("X-Signature", statusHeaders.signature()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.verifiedAt").isNotEmpty());

        String smsPath = "/api/v1/verifications/" + verificationId + "/sms";
        SignedHeaders listHeaders = signApi("GET", smsPath, "");
        mockMvc.perform(get(smsPath)
                        .header("X-Timestamp", listHeaders.timestamp())
                        .header("X-Nonce", listHeaders.nonce())
                        .header("X-Signature", listHeaders.signature()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationId").value(verificationId))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].senderPhone").value("+6281234567890"))
                .andExpect(jsonPath("$.items[0].text").value("VERIF " + code))
                .andExpect(jsonPath("$.items[0].matchStatus").value("MATCHED"));
    }

    @Test
    void replacingPendingSessionExpiresThePreviousSession() throws Exception {
        String body = "{\"phoneNumber\":\"081234567890\"}";

        SignedHeaders firstHeaders = signApi("POST", "/api/v1/verifications", body);
        MvcResult firstResult = mockMvc.perform(post("/api/v1/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Timestamp", firstHeaders.timestamp())
                        .header("X-Nonce", firstHeaders.nonce())
                        .header("X-Signature", firstHeaders.signature()))
                .andExpect(status().isCreated())
                .andReturn();
        String firstId = objectMapper.readTree(firstResult.getResponse().getContentAsByteArray())
                .get("verificationId")
                .asText();

        SignedHeaders secondHeaders = signApi("POST", "/api/v1/verifications", body);
        MvcResult secondResult = mockMvc.perform(post("/api/v1/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Timestamp", secondHeaders.timestamp())
                        .header("X-Nonce", secondHeaders.nonce())
                        .header("X-Signature", secondHeaders.signature()))
                .andExpect(status().isCreated())
                .andReturn();
        String secondId = objectMapper.readTree(secondResult.getResponse().getContentAsByteArray())
                .get("verificationId")
                .asText();

        assertThat(secondId).isNotEqualTo(firstId);

        String firstStatusPath = "/api/v1/verifications/" + firstId + "/status";
        SignedHeaders firstStatusHeaders = signApi("GET", firstStatusPath, "");
        mockMvc.perform(get(firstStatusPath)
                        .header("X-Timestamp", firstStatusHeaders.timestamp())
                        .header("X-Nonce", firstStatusHeaders.nonce())
                        .header("X-Signature", firstStatusHeaders.signature()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));

        String secondStatusPath = "/api/v1/verifications/" + secondId + "/status";
        SignedHeaders secondStatusHeaders = signApi("GET", secondStatusPath, "");
        mockMvc.perform(get(secondStatusPath)
                        .header("X-Timestamp", secondStatusHeaders.timestamp())
                        .header("X-Nonce", secondStatusHeaders.nonce())
                        .header("X-Signature", secondStatusHeaders.signature()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void returnsBadRequestForMalformedVerificationId() throws Exception {
        String target = "/api/v1/verifications/not-a-uuid/status";
        SignedHeaders headers = signApi("GET", target, "");

        mockMvc.perform(get(target)
                        .header("X-Timestamp", headers.timestamp())
                        .header("X-Nonce", headers.nonce())
                        .header("X-Signature", headers.signature()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PATH_PARAMETER"));
    }

    @Test
    void rejectsUnsignedStaleAndReplayedApiRequests() throws Exception {
        String body = "{\"phoneNumber\":\"081234567890\"}";

        mockMvc.perform(post("/api/v1/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("HMAC_HEADERS_REQUIRED"));

        String staleTimestamp = String.valueOf(Instant.now().minusSeconds(3600).getEpochSecond());
        String staleNonce = UUID.randomUUID().toString();
        String staleSignature = apiSignature("POST", "/api/v1/verifications", body, staleTimestamp, staleNonce);
        mockMvc.perform(post("/api/v1/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Timestamp", staleTimestamp)
                        .header("X-Nonce", staleNonce)
                        .header("X-Signature", staleSignature))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("HMAC_TIMESTAMP_INVALID"));

        SignedHeaders headers = signApi("POST", "/api/v1/verifications", body);
        mockMvc.perform(post("/api/v1/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Timestamp", headers.timestamp())
                        .header("X-Nonce", headers.nonce())
                        .header("X-Signature", headers.signature()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Timestamp", headers.timestamp())
                        .header("X-Nonce", headers.nonce())
                        .header("X-Signature", headers.signature()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("HMAC_REPLAY_DETECTED"));
    }

    @Test
    void rejectsInvalidAndroidSignatureAndKeepsWrongSenderPending() throws Exception {
        String createBody = "{\"phoneNumber\":\"081234567890\"}";
        SignedHeaders createHeaders = signApi("POST", "/api/v1/verifications", createBody);
        MvcResult createResult = mockMvc.perform(post("/api/v1/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("X-Timestamp", createHeaders.timestamp())
                        .header("X-Nonce", createHeaders.nonce())
                        .header("X-Signature", createHeaders.signature()))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsByteArray());
        String verificationId = created.get("verificationId").asText();
        String code = created.get("code").asText();

        String body = objectMapper.writeValueAsString(new SmsForwarderPayload(
                "+6281299999999", "VERIF " + code, null, Instant.now().toEpochMilli(), "SIM1"
        ));

        mockMvc.perform(post("/internal/sms/incoming")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Signature", "00".repeat(32)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("HMAC_SIGNATURE_INVALID"));

        String validSignature = hmacService.hmacSha256Hex(SMS_SECRET, body.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(post("/internal/sms/incoming")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Signature", validSignature))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchStatus").value("PHONE_MISMATCH"));

        String statusPath = "/api/v1/verifications/" + verificationId + "/status";
        SignedHeaders statusHeaders = signApi("GET", statusPath, "");
        mockMvc.perform(get(statusPath)
                        .header("X-Timestamp", statusHeaders.timestamp())
                        .header("X-Nonce", statusHeaders.nonce())
                        .header("X-Signature", statusHeaders.signature()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void exposesCurrentInMemoryStateViaDebugEndpoint() throws Exception {
        String createBody = "{\"phoneNumber\":\"081234567890\"}";
        SignedHeaders createHeaders = signApi("POST", "/api/v1/verifications", createBody);
        MvcResult createResult = mockMvc.perform(post("/api/v1/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("X-Timestamp", createHeaders.timestamp())
                        .header("X-Nonce", createHeaders.nonce())
                        .header("X-Signature", createHeaders.signature()))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsByteArray());
        String verificationId = created.get("verificationId").asText();
        String code = created.get("code").asText();

        String smsBody = objectMapper.writeValueAsString(new SmsForwarderPayload(
                "+6281234567890",
                "VERIF " + code,
                Instant.now().minusSeconds(1).toEpochMilli(),
                Instant.now().toEpochMilli(),
                "SIM1"
        ));
        String smsSignature = hmacService.hmacSha256Hex(SMS_SECRET, smsBody.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(post("/internal/sms/incoming")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(smsBody)
                        .header("X-Signature", smsSignature))
                .andExpect(status().isOk());

        String debugPath = "/api/v1/debug/storage";
        SignedHeaders debugHeaders = signApi("GET", debugPath, "");
        mockMvc.perform(get(debugPath)
                        .header("X-Timestamp", debugHeaders.timestamp())
                        .header("X-Nonce", debugHeaders.nonce())
                        .header("X-Signature", debugHeaders.signature()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationsCount").value(1))
                .andExpect(jsonPath("$.incomingSmsCount").value(1))
                .andExpect(jsonPath("$.noncesCount").value(2))
                .andExpect(jsonPath("$.verifications[0].id").value(verificationId))
                .andExpect(jsonPath("$.verifications[0].status").value("VERIFIED"))
                .andExpect(jsonPath("$.incomingSms[0].verificationId").value(verificationId))
                .andExpect(jsonPath("$.incomingSms[0].matchStatus").value("MATCHED"));
    }

    @Test
    void exposesUnsignedHealthEndpoint() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("sms-verification-gateway"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void exposesUnsignedVersionEndpoint() throws Exception {
        mockMvc.perform(get("/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("sms-verification-gateway"))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void allowsCorsPreflightForApiEndpointsWithoutHmac() throws Exception {
        mockMvc.perform(options("/api/v1/verifications")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type, X-Timestamp, X-Nonce, X-Signature"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsString("X-Timestamp")))
                .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsString("X-Nonce")))
                .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsString("X-Signature")));
    }

    @Test
    void allowsCrossOriginSignedApiRequestsAfterPreflight() throws Exception {
        String body = "{\"phoneNumber\":\"081234567890\"}";
        SignedHeaders headers = signApi("POST", "/api/v1/verifications", body);

        mockMvc.perform(post("/api/v1/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Origin", "http://localhost:3000")
                        .header("X-Timestamp", headers.timestamp())
                        .header("X-Nonce", headers.nonce())
                        .header("X-Signature", headers.signature()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    private SignedHeaders signApi(String method, String requestTarget, String body) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        return new SignedHeaders(timestamp, nonce, apiSignature(method, requestTarget, body, timestamp, nonce));
    }

    private String apiSignature(String method, String requestTarget, String body, String timestamp, String nonce) {
        String bodyHash = hmacService.sha256Hex(body.getBytes(StandardCharsets.UTF_8));
        String canonical = method + "\n" + requestTarget + "\n" + timestamp + "\n" + nonce + "\n" + bodyHash;
        return hmacService.hmacSha256Hex(API_SECRET, canonical.getBytes(StandardCharsets.UTF_8));
    }

    private record SignedHeaders(String timestamp, String nonce, String signature) {
    }

    private record SmsForwarderPayload(
            String from,
            String text,
            Long sentStamp,
            Long receivedStamp,
            String sim
    ) {
    }
}
