package com.paymentx.controlcenter.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.controlcenter.config.ControlCenterProperties;
import com.paymentx.controlcenter.dto.notification.MailHogStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * ENGLISH: The real HTTP client for MailHog's own API - GET
 * {baseUrl}/api/v2/messages, reading only its real "total" field (the
 * real count of SMTP messages MailHog has actually captured). What it
 * does NOT do: fetch, parse, or return any message body/subject/
 * recipient - that content is only ever viewed by a human opening
 * MailHog's own real web UI directly (webUiUrl), never routed through
 * this backend. A connection failure is reported honestly as
 * reachable=false, never silently treated as "zero messages". Why it
 * exists: the Phase 4 "if MailHog is configured, provide safe
 * preview/link" requirement - real reachability + real count + a real
 * link, nothing fabricated.
 *
 * HINGLISH: MailHog ke apne API ke liye real HTTP client - GET
 * {baseUrl}/api/v2/messages, sirf uska real "total" field padhte hue
 * (real count jo MailHog ne actually SMTP messages capture kiye). Ye
 * kya NAHI karti: koi message body/subject/recipient fetch, parse, ya
 * return nahi karti - wo content sirf ek insaan dwara MailHog ke apne
 * real web UI ko directly khol kar dekha jaata hai (webUiUrl), is
 * backend ke through kabhi route nahi hota. Ek connection failure ko
 * honestly reachable=false report kiya jaata hai, kabhi silently "zero
 * messages" nahi treat kiya jaata. Ye dashboard me kyu hai: Phase 4 ka
 * "agar MailHog configured hai, toh safe preview/link do" requirement
 * - real reachability + real count + ek real link, kuch fabricate
 * nahi kiya gaya.
 */
@Component
public class MailHogClient {

    private final RestTemplate restTemplate;
    private final ControlCenterProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MailHogClient(RestTemplate mailHogRestTemplate, ControlCenterProperties properties) {
        this.restTemplate = mailHogRestTemplate;
        this.properties = properties;
    }

    public MailHogStatus status() {
        var mailhog = properties.getMailhog();
        String url = mailhog.getBaseUrl() + "/api/v2/messages?limit=1";
        try {
            // Real MailHog serves this endpoint as Content-Type "text/json", not the standard "application/json" -
            // RestTemplate's default Jackson converter only negotiates "application/json", so getForObject(..,
            // JsonNode.class) throws HttpMessageNotReadableException against the real, live MailHog response.
            // Fetching as a raw String and parsing it ourselves sidesteps that content-type negotiation entirely.
            String rawBody = restTemplate.getForObject(url, String.class);
            JsonNode body = rawBody != null ? objectMapper.readTree(rawBody) : null;
            long total = body != null ? body.path("total").asLong(0) : 0;
            return new MailHogStatus(true, null, total, mailhog.getBaseUrl());
        } catch (ResourceAccessException connectionFailure) {
            return new MailHogStatus(false, "Could not reach MailHog: " + connectionFailure.getMostSpecificCause().getMessage(),
                    null, mailhog.getBaseUrl());
        } catch (RestClientException httpError) {
            return new MailHogStatus(false, "MailHog API call failed: " + httpError.getMessage(), null, mailhog.getBaseUrl());
        } catch (com.fasterxml.jackson.core.JsonProcessingException parseFailure) {
            return new MailHogStatus(false, "MailHog returned an unparseable response: " + parseFailure.getMessage(), null, mailhog.getBaseUrl());
        }
    }
}
