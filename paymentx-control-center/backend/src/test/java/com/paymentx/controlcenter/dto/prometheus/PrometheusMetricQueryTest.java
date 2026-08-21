package com.paymentx.controlcenter.dto.prometheus;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ENGLISH: Proves the Prometheus named-query catalog is the actual
 * "no raw PromQL from the browser" boundary - every slug is unique
 * (so fromSlug() can never resolve ambiguously), every PromQL string
 * is non-blank, and an unrecognised slug is rejected rather than
 * treated as free-form PromQL.
 *
 * HINGLISH: Prove karta hai ki Prometheus named-query catalog actual
 * "browser se raw PromQL nahi" boundary hai - har slug unique hai
 * (taaki fromSlug() kabhi ambiguously resolve na ho), har PromQL
 * string non-blank hai, aur ek unrecognised slug free-form PromQL ki
 * tarah treat hone ke bajaye reject hota hai.
 */
class PrometheusMetricQueryTest {

    @Test
    void everySlugIsUnique() {
        Set<String> slugs = new HashSet<>();
        for (PrometheusMetricQuery q : PrometheusMetricQuery.values()) {
            assertThat(slugs.add(q.slug())).as("duplicate slug: " + q.slug()).isTrue();
        }
    }

    @Test
    void everyQueryHasNonBlankPromQl() {
        assertThat(Arrays.stream(PrometheusMetricQuery.values()).map(PrometheusMetricQuery::promQl))
                .allSatisfy(promQl -> assertThat(promQl).isNotBlank());
    }

    @Test
    void fromSlugRejectsArbitraryPromQlInjectedAsASlug() {
        assertThatThrownBy(() -> PrometheusMetricQuery.fromSlug("up{job=~\".*\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
