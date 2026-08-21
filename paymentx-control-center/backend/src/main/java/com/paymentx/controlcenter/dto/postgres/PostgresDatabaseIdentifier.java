package com.paymentx.controlcenter.dto.postgres;

import com.paymentx.controlcenter.config.ControlCenterProperties;

import java.util.function.Function;

/**
 * ENGLISH: The fixed, allowlisted set of the 7 real, Liquibase-managed
 * PaymentX databases this backend is allowed to inspect (paymentx_auth
 * has no schema, so it is deliberately absent - verified during
 * earlier PaymentX work). What it does: pairs each database's stable
 * slug with the Spring bean qualifier name of its DataSource and a
 * function reading its real configured name. Why it exists: same SSRF-
 * style allowlist pattern as ServiceIdentifier - the connection/table
 * -info endpoints accept only this enum (bound from a path segment),
 * never a raw connection string or database name from the browser.
 *
 * HINGLISH: 7 real, Liquibase-managed PaymentX databases ka fixed,
 * allowlisted set jinhe ye backend inspect karne ki ijazat hai
 * (paymentx_auth ka koi schema nahi hai, isliye jaan-boojh kar absent
 * hai - pehle ke PaymentX work me verify kiya gaya). Ye kya karti hai:
 * har database ke stable slug ko uske DataSource ke Spring bean
 * qualifier name aur uska real configured naam padhne wale function
 * ke saath pair karta hai. Ye dashboard me kyu hai: ServiceIdentifier
 * jaisa hi SSRF-style allowlist pattern - connection/table-info
 * endpoints sirf ye enum accept karte hain (path segment se bind), na
 * ki browser se raw connection string ya database name.
 */
public enum PostgresDatabaseIdentifier {
    VALIDATION("validation", "validationDataSource", ControlCenterProperties.Postgres::getValidationDb),
    PAYMENT("payment", "paymentDataSource", ControlCenterProperties.Postgres::getPaymentDb),
    ROUTING("routing", "routingDataSource", ControlCenterProperties.Postgres::getRoutingDb),
    AUDIT("audit", "auditDataSource", ControlCenterProperties.Postgres::getAuditDb),
    NOTIFICATION("notification", "notificationDataSource", ControlCenterProperties.Postgres::getNotificationDb),
    RECONCILIATION("reconciliation", "reconciliationDataSource", ControlCenterProperties.Postgres::getReconciliationDb),
    REPORTING("reporting", "reportingDataSource", ControlCenterProperties.Postgres::getReportingDb);

    private final String slug;
    private final String dataSourceBeanName;
    private final Function<ControlCenterProperties.Postgres, String> databaseNameResolver;

    PostgresDatabaseIdentifier(String slug, String dataSourceBeanName, Function<ControlCenterProperties.Postgres, String> databaseNameResolver) {
        this.slug = slug;
        this.dataSourceBeanName = dataSourceBeanName;
        this.databaseNameResolver = databaseNameResolver;
    }

    public String slug() {
        return slug;
    }

    public String dataSourceBeanName() {
        return dataSourceBeanName;
    }

    public String resolveDatabaseName(ControlCenterProperties.Postgres postgres) {
        return databaseNameResolver.apply(postgres);
    }

    public static PostgresDatabaseIdentifier fromSlug(String slug) {
        for (PostgresDatabaseIdentifier value : values()) {
            if (value.slug.equals(slug)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown Postgres database identifier: " + slug);
    }
}
