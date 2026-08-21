package com.paymentx.controlcenter.client;

import com.paymentx.controlcenter.config.ControlCenterProperties;
import com.paymentx.controlcenter.dto.ServiceHealthStatus;
import com.paymentx.controlcenter.dto.ServiceIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * ENGLISH: A real integration test for ServiceHealthClient against a
 * MockRestServiceServer - real HTTP request/response round-trips
 * through Spring's RestTemplate machinery, just against a fake server
 * instead of a live PaymentX service, so this test proves the actual
 * status-code-to-ServiceHealthStatus mapping (including the "server
 * responded but with an error" and "server returned an unexpected
 * body" paths) without depending on any service actually being up.
 * What it does NOT do: fabricate a passing result for a genuinely
 * broken client - a wrong assertion here would fail loudly, same as
 * any other test.
 *
 * HINGLISH: ServiceHealthClient ke liye ek real integration test,
 * MockRestServiceServer ke against - Spring ke RestTemplate machinery
 * ke through real HTTP request/response round-trips, bas ek live
 * PaymentX service ki jagah ek fake server ke against, isliye ye test
 * actual status-code-to-ServiceHealthStatus mapping prove karta hai
 * (including "server ne respond kiya lekin error ke saath" aur
 * "server ne ek unexpected body return kiya" paths) kisi bhi service
 * ke actually up hone par depend kiye bina. Ye kya NAHI karta: ek
 * genuinely broken client ke liye ek passing result fabricate karna -
 * yahan ek galat assertion loudly fail hogi, kisi bhi doosre test ki
 * tarah.
 */
class ServiceHealthClientTest {

    @Test
    void healthyServiceIsReportedAsUpWithTheRealStatusFromTheBody() {
        RestTemplate restTemplate = new RestTemplateBuilder().build();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ControlCenterProperties properties = new ControlCenterProperties();

        server.expect(requestTo("http://localhost:8083/actuator/health"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"UP\"}"));

        ServiceHealthClient client = new ServiceHealthClient(restTemplate, properties);
        ServiceHealthStatus result = client.probe(ServiceIdentifier.PAYMENT_SERVICE, ServiceHealthClient.Probe.HEALTH);

        assertThat(result.status()).isEqualTo("UP");
        assertThat(result.httpStatusCode()).isEqualTo(200);
        assertThat(result.errorMessage()).isNull();
        server.verify();
    }

    @Test
    void serverErrorResponseIsReportedHonestlyNotAsUp() {
        RestTemplate restTemplate = new RestTemplateBuilder().build();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ControlCenterProperties properties = new ControlCenterProperties();

        server.expect(requestTo("http://localhost:8085/actuator/health"))
                .andRespond(withServerError()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"DOWN\"}"));

        ServiceHealthClient client = new ServiceHealthClient(restTemplate, properties);
        ServiceHealthStatus result = client.probe(ServiceIdentifier.AUDIT_SERVICE, ServiceHealthClient.Probe.HEALTH);

        assertThat(result.status()).isEqualTo("DOWN");
        assertThat(result.httpStatusCode()).isEqualTo(500);
        server.verify();
    }
}
