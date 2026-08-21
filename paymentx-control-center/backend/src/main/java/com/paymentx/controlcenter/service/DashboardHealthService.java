package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.dto.HealthResponse;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * ENGLISH: Builds the HealthResponse this backend reports about itself.
 * What it does: reads the real active Spring profile and application
 * name from the live Environment (never hardcoded), and the real
 * package implementation version if this jar was built with one -
 * falls back to "dev" honestly when running from an IDE/unpackaged
 * classpath rather than fabricating a version string. Why it exists:
 * keeps HealthController a thin HTTP adapter - the actual "what is our
 * health" logic lives here so Phase 2 can extend it (e.g. aggregate
 * real downstream-service reachability) without touching the controller.
 * How it will communicate with the backend: called by HealthController;
 * in Phase 2 this is where calls to the real PaymentX services'
 * /actuator/health endpoints would be aggregated.
 *
 * HINGLISH: Ye is backend ka apne baare me HealthResponse banata hai.
 * Ye kya karti hai: live Environment se real active Spring profile aur
 * application name padhta hai (kabhi hardcoded nahi), aur agar is jar
 * ka koi implementation version build hua ho toh wahi real version -
 * agar IDE/unpackaged classpath se chal raha ho toh honestly "dev" par
 * fallback karta hai, koi version string banata nahi. Ye dashboard me
 * kyu hai: HealthController ko ek thin HTTP adapter rakhta hai - "hamari
 * health kya hai" ka actual logic yahan rehta hai taaki Phase 2 ise
 * badha sake (jaise real downstream-service reachability aggregate
 * karna) bina controller ko chhue. Backend se kaise connect hogi:
 * HealthController ise call karta hai; Phase 2 me yahin par real
 * PaymentX services ke /actuator/health endpoints aggregate honge.
 */
@Service
public class DashboardHealthService {

    private final Environment environment;

    public DashboardHealthService(Environment environment) {
        this.environment = environment;
    }

    public HealthResponse currentHealth() {
        String applicationName = environment.getProperty("spring.application.name", "paymentx-control-center-backend");
        String[] activeProfiles = environment.getActiveProfiles();
        String activeProfile = activeProfiles.length > 0 ? String.join(",", activeProfiles) : "default";
        String version = getClass().getPackage().getImplementationVersion();
        if (version == null) {
            version = "dev";
        }
        return new HealthResponse("UP", applicationName, activeProfile, version, OffsetDateTime.now());
    }
}
