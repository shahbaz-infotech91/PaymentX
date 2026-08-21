package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.config.ControlCenterProperties;
import com.paymentx.controlcenter.dto.ServiceIdentifier;
import com.paymentx.controlcenter.dto.apitester.ApiTesterEndpointDescriptor;
import com.paymentx.controlcenter.dto.apitester.ApiTesterRequest;
import com.paymentx.controlcenter.dto.apitester.ApiTesterResponse;
import com.paymentx.controlcenter.exception.ControlCenterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ENGLISH: Executes exactly one ApiTesterAllowlist-matched call per
 * request, and nothing else - this is the entire Phase 5 API Tester
 * "no arbitrary URLs" guarantee made real. What it does: rejects any
 * (service, method, path) that ApiTesterAllowlist.match() doesn't
 * recognize BEFORE building any outbound request; resolves the real
 * base URL only through ServiceIdentifier (never from request input);
 * reuses the same connect/read-timeout-bounded RestTemplate every
 * other real service call in this backend already uses (so a hung
 * downstream service can never hang this endpoint indefinitely); and
 * never logs a request/response header or body - only method, path,
 * status, and duration - so a real Authorization/X-Api-Key/JWT the
 * user pastes into the tester is never written to this backend's own
 * logs. Why it exists: the actual Phase 5 API Tester security
 * requirement, satisfied at the one place every call passes through.
 *
 * HINGLISH: Har request ke liye exactly ek ApiTesterAllowlist-matched
 * call execute karta hai, aur kuch nahi - yehi poora Phase 5 API
 * Tester "arbitrary URLs nahi" guarantee real banaya gaya hai. Ye kya
 * karti hai: kisi bhi outbound request banane se PEHLE us
 * (service, method, path) ko reject karta hai jise
 * ApiTesterAllowlist.match() recognize na kare; real base URL sirf
 * ServiceIdentifier ke through resolve karta hai (kabhi request input
 * se nahi); wahi connect/read-timeout-bounded RestTemplate reuse karta
 * hai jo is backend ka har doosra real service call already use karta
 * hai (taaki ek hung downstream service is endpoint ko kabhi hamesha
 * ke liye hang na kar sake); aur kabhi kisi request/response header ya
 * body ko log nahi karta - sirf method, path, status, aur duration -
 * taaki user jo real Authorization/X-Api-Key/JWT tester me paste kare
 * wo kabhi is backend ke apne logs me na likha jaaye. Ye dashboard me
 * kyu hai: yehi actual Phase 5 API Tester security requirement hai, us
 * ek jagah satisfy kiya gaya jahan se har call guzarta hai.
 */
@Service
public class ApiTesterService {

    private static final Logger log = LoggerFactory.getLogger(ApiTesterService.class);
    private static final Set<String> STRIPPED_REQUEST_HEADERS = Set.of("host", "content-length");
    private static final Set<String> METHODS_WITH_BODY = Set.of("POST", "PUT", "PATCH");

    private final RestTemplate restTemplate;
    private final ControlCenterProperties properties;
    private final ApiTesterAllowlist allowlist;

    public ApiTesterService(@Qualifier("paymentXServiceRestTemplate") RestTemplate restTemplate,
                             ControlCenterProperties properties,
                             ApiTesterAllowlist allowlist) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.allowlist = allowlist;
    }

    public List<ApiTesterEndpointDescriptor> allowedEndpoints() {
        return allowlist.all();
    }

    public ApiTesterResponse execute(ApiTesterRequest request) {
        String method = request.method() != null ? request.method().toUpperCase() : null;
        ApiTesterEndpointDescriptor descriptor = allowlist.match(request.service(), method, request.path())
                .orElseThrow(() -> new IllegalArgumentException(
                        "This service/method/path is not on the API Tester's explicit allowlist: "
                                + request.service() + " " + method + " " + request.path()));

        ServiceIdentifier identifier = ServiceIdentifier.fromSlug(descriptor.service());
        String baseUrl = identifier.resolveBaseUrl(properties.getServices());

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(baseUrl).path(request.path());
        if (request.queryParams() != null) {
            request.queryParams().forEach((k, v) -> {
                if (k != null && !k.isBlank()) uriBuilder.queryParam(k, v);
            });
        }
        URI uri = uriBuilder.build(true).toUri();

        HttpHeaders headers = new HttpHeaders();
        if (request.headers() != null) {
            request.headers().forEach((name, value) -> {
                if (name != null && !STRIPPED_REQUEST_HEADERS.contains(name.toLowerCase())) {
                    headers.add(name, value);
                }
            });
        }
        boolean carriesBody = METHODS_WITH_BODY.contains(method) && request.body() != null && !request.body().isBlank();
        if (carriesBody && headers.getContentType() == null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        HttpEntity<String> entity = new HttpEntity<>(carriesBody ? request.body() : null, headers);

        long start = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.valueOf(method), entity, String.class);
            long elapsed = System.currentTimeMillis() - start;
            log.info("API Tester call service={} method={} path={} status={} durationMs={}",
                    descriptor.service(), method, request.path(), response.getStatusCode().value(), elapsed);
            return toResponse(response.getStatusCode().value(), response.getStatusCode().toString(), response.getHeaders(), response.getBody(), elapsed);
        } catch (RestClientResponseException httpError) {
            long elapsed = System.currentTimeMillis() - start;
            log.info("API Tester call service={} method={} path={} status={} durationMs={}",
                    descriptor.service(), method, request.path(), httpError.getStatusCode().value(), elapsed);
            return toResponse(httpError.getStatusCode().value(), httpError.getStatusText(),
                    httpError.getResponseHeaders(), httpError.getResponseBodyAsString(), elapsed);
        } catch (ResourceAccessException connectionFailure) {
            long elapsed = System.currentTimeMillis() - start;
            log.info("API Tester call service={} method={} path={} status=UNREACHABLE durationMs={}",
                    descriptor.service(), method, request.path(), elapsed);
            throw new ControlCenterException("UPSTREAM_UNREACHABLE",
                    "Could not reach " + identifier.displayName() + ": " + connectionFailure.getMostSpecificCause().getMessage());
        }
    }

    private ApiTesterResponse toResponse(int status, String statusText, HttpHeaders responseHeaders, String body, long elapsedMillis) {
        Map<String, String> flatHeaders = new LinkedHashMap<>();
        if (responseHeaders != null) {
            responseHeaders.forEach((name, values) -> flatHeaders.put(name, String.join(", ", values)));
        }
        return new ApiTesterResponse(status, statusText, flatHeaders, body, elapsedMillis);
    }
}
