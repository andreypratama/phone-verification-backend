package com.smsverification.gateway.security;

import com.smsverification.gateway.config.AppProperties;
import com.smsverification.gateway.core.crypto.HmacService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.DateTimeException;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HmacAuthenticationFilter extends OncePerRequestFilter {

    private static final String SMS_FORWARDER_PATH = "/internal/sms/incoming";
    private static final String SIGNATURE_HEADER = "X-Signature";
    private static final String TIMESTAMP_HEADER = "X-Timestamp";
    private static final String NONCE_HEADER = "X-Nonce";
    private static final Pattern NONCE_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{16,128}$");
    private static final Set<String> PUBLIC_PATHS = Set.of("/health", "/version");

    private final AppProperties properties;
    private final HmacService hmacService;
    private final HmacNonceService nonceService;
    private final ApiErrorWriter errorWriter;
    private final Clock clock;

    public HmacAuthenticationFilter(
            AppProperties properties,
            HmacService hmacService,
            HmacNonceService nonceService,
            ApiErrorWriter errorWriter,
            Clock clock
    ) {
        this.properties = properties;
        this.hmacService = hmacService;
        this.nonceService = nonceService;
        this.errorWriter = errorWriter;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);

        boolean authenticated = SMS_FORWARDER_PATH.equals(request.getRequestURI())
                ? authenticateSmsForwarder(cachedRequest, response)
                : authenticateApiRequest(cachedRequest, response);

        if (authenticated) {
            filterChain.doFilter(cachedRequest, response);
        }
    }

    private boolean authenticateSmsForwarder(
            CachedBodyHttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        String suppliedSignature = trimToNull(request.getHeader(SIGNATURE_HEADER));
        if (suppliedSignature == null) {
            errorWriter.unauthorized(
                    request,
                    response,
                    "HMAC_HEADERS_REQUIRED",
                    "X-Signature header is required"
            );
            return false;
        }

        String expectedSignature = hmacService.hmacSha256Hex(
                properties.security().smsForwarderHmacSecret(),
                request.getCachedBody()
        );
        if (!hmacService.constantTimeHexEquals(expectedSignature, suppliedSignature)) {
            errorWriter.unauthorized(
                    request,
                    response,
                    "HMAC_SIGNATURE_INVALID",
                    "HMAC signature is invalid"
            );
            return false;
        }
        return true;
    }

    private boolean authenticateApiRequest(
            CachedBodyHttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        String timestampValue = trimToNull(request.getHeader(TIMESTAMP_HEADER));
        String nonce = trimToNull(request.getHeader(NONCE_HEADER));
        String suppliedSignature = trimToNull(request.getHeader(SIGNATURE_HEADER));

        if (timestampValue == null || nonce == null || suppliedSignature == null) {
            errorWriter.unauthorized(
                    request,
                    response,
                    "HMAC_HEADERS_REQUIRED",
                    "X-Timestamp, X-Nonce, and X-Signature headers are required"
            );
            return false;
        }

        Instant requestTimestamp = parseTimestamp(timestampValue);
        Instant now = Instant.now(clock);
        Duration allowedSkew = properties.security().allowedClockSkew();
        if (requestTimestamp == null
                || requestTimestamp.isBefore(now.minus(allowedSkew))
                || requestTimestamp.isAfter(now.plus(allowedSkew))) {
            errorWriter.unauthorized(
                    request,
                    response,
                    "HMAC_TIMESTAMP_INVALID",
                    "X-Timestamp is invalid or outside the allowed clock skew"
            );
            return false;
        }

        if (!NONCE_PATTERN.matcher(nonce).matches()) {
            errorWriter.unauthorized(
                    request,
                    response,
                    "HMAC_NONCE_INVALID",
                    "X-Nonce must contain 16 to 128 safe characters"
            );
            return false;
        }

        String requestTarget = request.getRequestURI();
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            requestTarget += "?" + request.getQueryString();
        }
        String bodyHash = hmacService.sha256Hex(request.getCachedBody());
        String canonicalRequest = request.getMethod().toUpperCase(Locale.ROOT)
                + "\n" + requestTarget
                + "\n" + timestampValue
                + "\n" + nonce
                + "\n" + bodyHash;
        String expectedSignature = hmacService.hmacSha256Hex(
                properties.security().apiHmacSecret(),
                canonicalRequest.getBytes(StandardCharsets.UTF_8)
        );

        if (!hmacService.constantTimeHexEquals(expectedSignature, suppliedSignature)) {
            errorWriter.unauthorized(
                    request,
                    response,
                    "HMAC_SIGNATURE_INVALID",
                    "HMAC signature is invalid"
            );
            return false;
        }

        boolean nonceClaimed = nonceService.claim(
                nonce,
                now,
                now.plus(allowedSkew.multipliedBy(2))
        );
        if (!nonceClaimed) {
            errorWriter.unauthorized(
                    request,
                    response,
                    "HMAC_REPLAY_DETECTED",
                    "X-Nonce has already been used"
            );
            return false;
        }
        return true;
    }

    private Instant parseTimestamp(String timestampValue) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(timestampValue));
        } catch (NumberFormatException | DateTimeException exception) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PUBLIC_PATHS.contains(request.getRequestURI());
    }
}
