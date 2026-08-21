package com.paymentx.notification.template;

import com.paymentx.notification.config.NotificationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

/**
 * WHY the CACHE here holds rendered HTML output (keyed by template name
 * + variable hash), not the Thymeleaf template FILES themselves:
 * Thymeleaf's own TemplateEngine already caches parsed template files
 * internally (its standard, well-tested resolver-level caching) - adding
 * a second cache layer for the same thing would be redundant. What
 * ISN'T already cached is the fully-rendered-with-variables HTML string,
 * which is real, repeatable work worth avoiding when the exact same
 * template+variables combination recurs.
 */
@Service
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * EmailTemplateService is a service in the notification module of PaymentX. It lives in package com.paymentx.notification.template and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * EmailTemplateService PaymentX ke notification module ka ek service hai. Ye com.paymentx.notification.template package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class EmailTemplateService {

    private static final String CACHE_KEY_PREFIX = "notification:template:";

    private final TemplateEngine templateEngine;
    private final StringRedisTemplate redisTemplate;
    private final NotificationProperties notificationProperties;

    public EmailTemplateService(TemplateEngine templateEngine, StringRedisTemplate redisTemplate,
                                 NotificationProperties notificationProperties) {
        this.templateEngine = templateEngine;
        this.redisTemplate = redisTemplate;
        this.notificationProperties = notificationProperties;
    }

    public String render(String templateName, Map<String, Object> variables) {
        String cacheKey = CACHE_KEY_PREFIX + templateName + ":" + variables.hashCode();

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("Template cache read failed, rendering fresh templateName={}", templateName, e);
        }

        Context context = new Context();
        context.setVariables(variables);
        String rendered = templateEngine.process(templateName, context);

        try {
            redisTemplate.opsForValue().set(cacheKey, rendered, notificationProperties.getCache().getTemplateTtl());
        } catch (Exception e) {
            log.warn("Template cache write failed templateName={}", templateName, e);
        }

        return rendered;
    }
}
