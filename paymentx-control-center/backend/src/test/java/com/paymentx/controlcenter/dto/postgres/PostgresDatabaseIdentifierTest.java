package com.paymentx.controlcenter.dto.postgres;

import com.paymentx.controlcenter.config.ControlCenterProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ENGLISH: Proves PostgresDatabaseIdentifier is exactly the 7 real,
 * Liquibase-managed databases (paymentx_auth deliberately excluded -
 * auth-service owns no database), each resolving to its real
 * configured database name and DataSource bean-qualifier name, and
 * that an unknown slug is rejected.
 *
 * HINGLISH: Proves karta hai ki PostgresDatabaseIdentifier exactly 7
 * real, Liquibase-managed databases hain (paymentx_auth jaan-boojh
 * kar exclude hai - auth-service koi database own nahi karta), har
 * ek apne real configured database name aur DataSource
 * bean-qualifier name par resolve hota hai, aur ek unknown slug
 * reject hota hai.
 */
class PostgresDatabaseIdentifierTest {

    @Test
    void exactlySevenDatabasesAreAllowlisted() {
        assertThat(PostgresDatabaseIdentifier.values()).hasSize(7);
    }

    @Test
    void everyIdentifierResolvesToARealConfiguredDatabaseName() {
        ControlCenterProperties.Postgres postgres = new ControlCenterProperties.Postgres();
        for (PostgresDatabaseIdentifier identifier : PostgresDatabaseIdentifier.values()) {
            String dbName = identifier.resolveDatabaseName(postgres);
            assertThat(dbName).as("database name for " + identifier).startsWith("paymentx_");
            assertThat(identifier.dataSourceBeanName()).endsWith("DataSource");
        }
    }

    @Test
    void fromSlugRejectsAnUnknownDatabase() {
        assertThatThrownBy(() -> PostgresDatabaseIdentifier.fromSlug("paymentx_auth"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
