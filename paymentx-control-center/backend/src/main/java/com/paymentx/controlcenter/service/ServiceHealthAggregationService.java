package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.client.ServiceHealthClient;
import com.paymentx.controlcenter.dto.ServiceHealthStatus;
import com.paymentx.controlcenter.dto.ServiceIdentifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * ENGLISH: Orchestrates real /actuator/health calls across all 9
 * PaymentX services for the "all services" dashboard view. What it
 * does: fans out one ServiceHealthClient.probe() call per
 * ServiceIdentifier concurrently (so 9 real HTTP round-trips do not
 * serialize into a 9x wait), and collects the real per-service results
 * - including any that are DOWN/UNREACHABLE, never dropping or hiding
 * a failure. Why it exists: the Services overview page needs one
 * endpoint that reports on all 9 real services at once; doing this
 * sequentially would make the page slow for no reason since each probe
 * is an independent, already-timeout-bounded HTTP call (see
 * ControlCenterProperties.Services connect/read timeouts). How it will
 * communicate with the backend: called by ServicesController.
 *
 * HINGLISH: Saare 9 PaymentX services ke across real /actuator/health
 * calls ko "all services" dashboard view ke liye orchestrate karta hai.
 * Ye kya karti hai: har ServiceIdentifier ke liye ek
 * ServiceHealthClient.probe() call ko concurrently fan-out karta hai
 * (taaki 9 real HTTP round-trips ek 9x wait me serialize na ho jayein),
 * aur real per-service results collect karta hai - kisi bhi
 * DOWN/UNREACHABLE ko bhi shamil karte hue, kabhi kisi failure ko drop
 * ya chhupata nahi. Ye dashboard me kyu hai: Services overview page ko
 * ek hi endpoint chahiye jo saare 9 real services ke baare me ek saath
 * report kare; ise sequentially karne se page bewajah slow ho jaata
 * kyunki har probe ek independent, pehle se timeout-bounded HTTP call
 * hai (dekho ControlCenterProperties.Services connect/read timeouts).
 * Backend se kaise connect hogi: ServicesController ise call karta hai.
 */
@Service
public class ServiceHealthAggregationService {

    private final ServiceHealthClient client;

    public ServiceHealthAggregationService(ServiceHealthClient client) {
        this.client = client;
    }

    public List<ServiceHealthStatus> checkAll() {
        List<CompletableFuture<ServiceHealthStatus>> futures = List.of(ServiceIdentifier.values()).stream()
                .map(service -> CompletableFuture.supplyAsync(() -> client.probe(service, ServiceHealthClient.Probe.HEALTH)))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    public ServiceHealthStatus checkOne(ServiceIdentifier service, ServiceHealthClient.Probe probe) {
        return client.probe(service, probe);
    }
}
