package com.paymentx.controlcenter.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * ENGLISH: Builds 7 small, read-only-intent HikariCP connection pools,
 * one per real Liquibase-managed PaymentX database, instead of the one
 * default Spring Boot DataSource this module deliberately does not
 * have (see application.yml's DataSourceAutoConfiguration exclusion).
 * What it does: each bean points at the same local Postgres instance
 * (host/port/username/password from ControlCenterProperties.Postgres)
 * but a different real database name, with a small pool size (this
 * backend only ever issues monitoring SELECTs, never a business
 * workload) and readOnly=true set on the Hikari config as a
 * defense-in-depth guard against any accidental write statement,
 * on top of the actual guard - every query this module runs is a
 * hardcoded, parameterized SELECT (see repository/*Repository.java),
 * never client-supplied SQL. Why it exists: paymentx_auth has no
 * schema (auth-service owns no database - verified during earlier
 * PaymentX observability work), so exactly 7 pools exist here, not 8.
 * How it will communicate with the backend: each @Qualifier'd
 * DataSource bean is injected into exactly one repository class in
 * repository/, one per business domain.
 *
 * HINGLISH: 7 chhote, read-only-intent HikariCP connection pools
 * banata hai, ek har real Liquibase-managed PaymentX database ke liye,
 * us ek default Spring Boot DataSource ki jagah jo is module ke paas
 * jaan-boojh kar nahi hai (application.yml ki
 * DataSourceAutoConfiguration exclusion dekho). Ye kya karti hai: har
 * bean usi local Postgres instance ko point karta hai
 * (host/port/username/password ControlCenterProperties.Postgres se)
 * lekin alag real database name ke saath, ek chhoti pool size ke saath
 * (ye backend kabhi bhi sirf monitoring SELECTs chalata hai, kabhi
 * business workload nahi), aur Hikari config par readOnly=true set
 * kiya gaya hai kisi bhi accidental write statement ke against
 * defense-in-depth guard ke roop me, us actual guard ke upar - is
 * module ka har query ek hardcoded, parameterized SELECT hai
 * (repository/*Repository.java dekho), kabhi client-supplied SQL
 * nahi. Ye dashboard me kyu hai: paymentx_auth ka koi schema nahi hai
 * (auth-service koi database own nahi karta - pehle ke PaymentX
 * observability work me verify kiya gaya), isliye yahan exactly 7
 * pools hain, 8 nahi. Backend se kaise connect hogi: har
 * @Qualifier'd DataSource bean repository/ ke exactly ek repository
 * class me inject hota hai, har business domain ke liye ek.
 */
@Configuration
public class PostgresDataSourceConfig {

    private final ControlCenterProperties properties;

    public PostgresDataSourceConfig(ControlCenterProperties properties) {
        this.properties = properties;
    }

    @Bean(name = "validationDataSource")
    public DataSource validationDataSource() {
        return build(properties.getPostgres().getValidationDb());
    }

    @Bean(name = "paymentDataSource")
    public DataSource paymentDataSource() {
        return build(properties.getPostgres().getPaymentDb());
    }

    @Bean(name = "routingDataSource")
    public DataSource routingDataSource() {
        return build(properties.getPostgres().getRoutingDb());
    }

    @Bean(name = "auditDataSource")
    public DataSource auditDataSource() {
        return build(properties.getPostgres().getAuditDb());
    }

    @Bean(name = "notificationDataSource")
    public DataSource notificationDataSource() {
        return build(properties.getPostgres().getNotificationDb());
    }

    @Bean(name = "reconciliationDataSource")
    public DataSource reconciliationDataSource() {
        return build(properties.getPostgres().getReconciliationDb());
    }

    @Bean(name = "reportingDataSource")
    public DataSource reportingDataSource() {
        return build(properties.getPostgres().getReportingDb());
    }

    private DataSource build(String databaseName) {
        ControlCenterProperties.Postgres pg = properties.getPostgres();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://" + pg.getHost() + ":" + pg.getPort() + "/" + databaseName);
        config.setUsername(pg.getUsername());
        config.setPassword(pg.getPassword());
        config.setMaximumPoolSize(pg.getMaxPoolSize());
        config.setMinimumIdle(1);
        config.setReadOnly(true);
        config.setPoolName(databaseName + "-pool");
        config.setConnectionTimeout(5000);
        config.setValidationTimeout(3000);
        return new HikariDataSource(config);
    }
}
