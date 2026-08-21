package com.paymentx.controlcenter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ENGLISH: Allows the Vite dev server (a different origin/port from this
 * backend) to call this backend's REST API during local development.
 * What it does: permits the configured frontend origins (see
 * control-center.cors.allowed-origins in application.yml, default
 * http://localhost:5173, Vite's default port) for GET/POST/PUT/DELETE
 * with credentials disabled (no cookies/session state crosses this
 * boundary - the dashboard is stateless). Why it exists: without this,
 * every browser fetch from the React app to this backend fails
 * same-origin policy the instant the two run on different ports, which
 * they always will locally (frontend :5173, backend :8089). How it will
 * communicate with the backend: this IS what makes frontend-to-backend
 * HTTP calls possible at all in local dev - src/api/axiosClient.ts's
 * baseURL points here.
 *
 * HINGLISH: Ye Vite dev server (jo is backend se alag origin/port par
 * chalta hai) ko local development ke dauraan is backend ka REST API
 * call karne deta hai. Ye kya karti hai: configured frontend origins
 * (application.yml me control-center.cors.allowed-origins dekho,
 * default http://localhost:5173, Vite ka default port) ko GET/POST/PUT/
 * DELETE ke liye permit karta hai, credentials disabled ke saath (koi
 * cookies/session state is boundary ko cross nahi karta - dashboard
 * stateless hai). Ye dashboard me kyu hai: iske bina, React app se is
 * backend tak har browser fetch same-origin policy par fail ho jayega
 * jaise hi dono alag ports par chalenge, jo locally hamesha honge
 * (frontend :5173, backend :8089). Backend se kaise connect hogi: yehi
 * hai jo local dev me frontend-se-backend HTTP calls ko possible banata
 * hai - src/api/axiosClient.ts ka baseURL yahin point karta hai.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final ControlCenterProperties properties;

    public CorsConfig(ControlCenterProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.getCors().getAllowedOrigins().toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
