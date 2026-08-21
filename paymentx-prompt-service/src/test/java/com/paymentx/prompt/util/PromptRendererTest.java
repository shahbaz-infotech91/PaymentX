package com.paymentx.prompt.util;

import com.paymentx.common.exception.PaymentXException;
import com.paymentx.prompt.dto.PromptVariable;
import com.paymentx.prompt.exception.PromptErrorCodes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * English:
 * Proves PromptRenderer's real substitution/validation rules in
 * isolation - no Spring context needed, this is pure logic (see the
 * class's own javadoc for why: plain regex substitution, no SpEL/
 * scripting engine of any kind). What it verifies: multiple-variable
 * substitution, a required-and-missing variable rejected
 * (MISSING_VARIABLE) - including null and blank-string treated
 * identically to absent, an unknown supplied variable rejected
 * (UNKNOWN_VARIABLE), an undeclared placeholder in the template itself
 * rejected (INVALID_PROMPT_CONTENT), and an optional (not required)
 * variable substituting as an empty string when not supplied - the
 * explicitly-designed exception Step 10 of the Phase 3.2 brief allows.
 * Why it exists: Step 9/10/25 of the Phase 3.2 brief - "render prompt,
 * render multiple variables, missing variable, unknown variable, null
 * variable, empty variable" is this test class's exact scope.
 * How it communicates with other components: exercises PromptRenderer
 * directly with no collaborators - PromptServiceImplTest covers the
 * layer that resolves which PromptVersion.content/variables get
 * fed into this class.
 *
 * Hinglish:
 * PromptRenderer ke real substitution/validation rules ko isolation me
 * prove karta hai - koi Spring context nahi chahiye, ye pure logic hai
 * (class ka apna javadoc dekho kyun: plain regex substitution, kisi
 * bhi tarah ka SpEL/scripting engine nahi). Ye kya verify karta hai:
 * multiple-variable substitution, ek required-aur-missing variable
 * reject hota hai (MISSING_VARIABLE) - null aur blank-string ko absent
 * jaisa hi treat karte hue, ek unknown supplied variable reject hota
 * hai (UNKNOWN_VARIABLE), template me khud ek undeclared placeholder
 * reject hota hai (INVALID_PROMPT_CONTENT), aur ek optional (required
 * nahi) variable supply na hone par empty string ke roop me substitute
 * hota hai - Phase 3.2 brief ka Step 10 jo explicitly-designed
 * exception allow karta hai.
 * Ye kyu hai: Phase 3.2 brief ka Step 9/10/25 - "render prompt, render
 * multiple variables, missing variable, unknown variable, null
 * variable, empty variable" isi test class ka exact scope hai.
 * Dusre components se kaise communicate karta hai: PromptRenderer ko
 * directly exercise karta hai, koi collaborators nahi -
 * PromptServiceImplTest us layer ko cover karta hai jo resolve karta
 * hai ki kaunsa PromptVersion.content/variables is class me feed hote
 * hain.
 */
class PromptRendererTest {

    private final PromptRenderer renderer = new PromptRenderer();

    @Test
    void render_substitutesMultipleVariables() {
        List<PromptVariable> variables = List.of(
                new PromptVariable("paymentReference", true, null),
                new PromptVariable("status", true, null),
                new PromptVariable("errorCode", true, null));
        String content = "Payment {{paymentReference}} is {{status}} with code {{errorCode}}.";

        PromptRenderer.RenderResult result = renderer.render(content, variables,
                Map.of("paymentReference", "PMT-123", "status", "FAILED", "errorCode", "PMT-409"));

        assertThat(result.renderedContent()).isEqualTo("Payment PMT-123 is FAILED with code PMT-409.");
        assertThat(result.variablesUsed()).containsExactlyInAnyOrder("paymentReference", "status", "errorCode");
    }

    @Test
    void render_missingRequiredVariable_throwsMissingVariable() {
        List<PromptVariable> variables = List.of(new PromptVariable("errorCode", true, null));
        assertThatThrownBy(() -> renderer.render("Failed: {{errorCode}}.", variables, Map.of()))
                .isInstanceOf(PaymentXException.class)
                .extracting(ex -> ((PaymentXException) ex).getErrorCode())
                .isEqualTo(PromptErrorCodes.MISSING_VARIABLE);
    }

    @Test
    void render_nullRequiredVariableValue_treatedAsMissing() {
        List<PromptVariable> variables = List.of(new PromptVariable("errorCode", true, null));
        Map<String, String> values = new java.util.HashMap<>();
        values.put("errorCode", null);

        assertThatThrownBy(() -> renderer.render("Failed: {{errorCode}}.", variables, values))
                .isInstanceOf(PaymentXException.class)
                .extracting(ex -> ((PaymentXException) ex).getErrorCode())
                .isEqualTo(PromptErrorCodes.MISSING_VARIABLE);
    }

    @Test
    void render_emptyStringRequiredVariableValue_treatedAsMissing() {
        List<PromptVariable> variables = List.of(new PromptVariable("errorCode", true, null));

        assertThatThrownBy(() -> renderer.render("Failed: {{errorCode}}.", variables, Map.of("errorCode", "   ")))
                .isInstanceOf(PaymentXException.class)
                .extracting(ex -> ((PaymentXException) ex).getErrorCode())
                .isEqualTo(PromptErrorCodes.MISSING_VARIABLE);
    }

    @Test
    void render_unknownSuppliedVariable_throwsUnknownVariable() {
        List<PromptVariable> variables = List.of(new PromptVariable("errorCode", true, null));

        assertThatThrownBy(() -> renderer.render("Failed: {{errorCode}}.", variables,
                Map.of("errorCode", "E1", "typoedName", "oops")))
                .isInstanceOf(PaymentXException.class)
                .extracting(ex -> ((PaymentXException) ex).getErrorCode())
                .isEqualTo(PromptErrorCodes.UNKNOWN_VARIABLE);
    }

    @Test
    void render_contentReferencesUndeclaredPlaceholder_throwsInvalidContent() {
        List<PromptVariable> variables = List.of(new PromptVariable("errorCode", true, null));

        assertThatThrownBy(() -> renderer.render("Failed: {{errorCode}} because {{reason}}.", variables, Map.of("errorCode", "E1")))
                .isInstanceOf(PaymentXException.class)
                .extracting(ex -> ((PaymentXException) ex).getErrorCode())
                .isEqualTo(PromptErrorCodes.INVALID_PROMPT_CONTENT);
    }

    @Test
    void render_optionalVariableNotSupplied_substitutesAsEmptyString() {
        List<PromptVariable> variables = List.of(
                new PromptVariable("errorCode", true, null),
                new PromptVariable("hint", false, null));

        PromptRenderer.RenderResult result = renderer.render("Error {{errorCode}}. Hint: {{hint}}.", variables, Map.of("errorCode", "E1"));

        assertThat(result.renderedContent()).isEqualTo("Error E1. Hint: .");
    }

    @Test
    void validateContentDeclaresOnlyKnownVariables_passesWhenAllPlaceholdersDeclared() {
        List<PromptVariable> variables = List.of(new PromptVariable("a", true, null), new PromptVariable("b", false, null));
        renderer.validateContentDeclaresOnlyKnownVariables("{{a}} and {{b}}", variables);
        // no exception = pass
    }
}
