package com.paymentx.prompt.util;

import com.paymentx.common.exception.PaymentXException;
import com.paymentx.prompt.dto.PromptVariable;
import com.paymentx.prompt.exception.PromptErrorCodes;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * English:
 * Safe variable substitution for prompt content - and ONLY that. WHY
 * plain regex find/replace instead of a templating engine (Mustache,
 * FreeMarker, Thymeleaf) or Spring's SpEL: Step 9 of the Phase 3.2
 * brief is explicit - "Prompt templates are DATA, not executable
 * code... do NOT evaluate SpEL or scripting languages from prompt
 * content." A templating engine can (even accidentally, via a
 * misconfigured mode) evaluate expressions, call methods, or walk
 * object graphs - none of that capability exists here at all, by
 * construction, because there is no expression evaluator anywhere in
 * this class: every substitution is a literal string replacement of a
 * `{{name}}` token with a caller-supplied plain String, nothing else
 * ever executes. Placeholder syntax is intentionally simple:
 * {@code \{\{\s*([a-zA-Z][a-zA-Z0-9_]*)\s*\}\}} - a name, optionally
 * surrounded by whitespace, inside double braces; anything else
 * (nested braces, expressions, function-call-looking syntax) is not a
 * recognized placeholder and is left as literal text in the output
 * rather than partially matched.
 *
 * <p>Validation order (see Step 10 of the Phase 3.2 brief): (1) every
 * placeholder actually present in {@code content} must correspond to a
 * declared variable - an undeclared placeholder means the template
 * itself is broken (INVALID_PROMPT_CONTENT; PromptServiceImpl already
 * checks this at create/version-create time, this is a defense-in-depth
 * repeat, not the primary enforcement point); (2) every key in the
 * caller-supplied {@code values} map must be a declared variable -
 * anything else is rejected (UNKNOWN_VARIABLE) rather than silently
 * ignored, since a typo'd variable name silently doing nothing would be
 * far more confusing than a real error; (3) every declared variable
 * marked {@code required=true} must have a non-blank value supplied -
 * missing/null/empty-string all count as missing (MISSING_VARIABLE);
 * (4) a declared variable marked {@code required=false} with no value
 * supplied substitutes as an empty string - the one deliberately
 * designed exception Step 10 allows ("avoid silently replacing missing
 * values... unless explicitly designed" - this is that explicit
 * design, see PromptVariable's javadoc for the same statement from the
 * data-model side).
 *
 * Why it exists: Step 9/10 of the Phase 3.2 brief, and the actual
 * "Rendered Prompt" deliverable PAYMENTX_PHASE_3_ARCHITECTURE.md §4
 * describes Prompt Service as producing.
 * How it communicates with other components: called only by
 * PromptServiceImpl.renderPrompt with one resolved PromptVersion's
 * content+variables and one RenderPromptRequest's values map - never
 * called directly by a controller, never calls out to any other
 * component itself.
 *
 * Hinglish:
 * Prompt content ke liye safe variable substitution - aur SIRF wahi.
 * Ek templating engine (Mustache, FreeMarker, Thymeleaf) ya Spring ke
 * SpEL ke bajaye plain regex find/replace KYU: Phase 3.2 brief ka Step
 * 9 explicit hai - "Prompt templates DATA hain, executable code nahi...
 * prompt content se SpEL ya scripting languages evaluate MAT karo." Ek
 * templating engine (chahe accidentally, ek misconfigured mode ke
 * through) expressions evaluate kar sakta hai, methods call kar sakta
 * hai, ya object graphs walk kar sakta hai - yahan wo capability bilkul
 * bhi exist nahi karti, construction se hi, kyunki is class me kahin
 * bhi koi expression evaluator hai hi nahi: har substitution ek `{{name}}`
 * token ka ek caller-supplied plain String se literal string replacement
 * hai, aur kuch bhi kabhi execute nahi hota. Placeholder syntax
 * jaan-boojh kar simple hai: {@code \{\{\s*([a-zA-Z][a-zA-Z0-9_]*)\s*\}\}}
 * - ek naam, optionally whitespace se surrounded, double braces ke
 * andar; kuch aur (nested braces, expressions, function-call-looking
 * syntax) ek recognized placeholder nahi hai aur output me literal text
 * ke roop me chhoda jaata hai, partially match nahi hota.
 *
 * <p>Validation order (Phase 3.2 brief ka Step 10 dekho): (1) content
 * me actually present har placeholder ek declared variable se match
 * karna chahiye - ek undeclared placeholder ka matlab hai template khud
 * broken hai (INVALID_PROMPT_CONTENT; PromptServiceImpl create/
 * version-create time par pehle se ye check karta hai, ye ek
 * defense-in-depth repeat hai, primary enforcement point nahi); (2)
 * caller-supplied {@code values} map ki har key ek declared variable
 * honi chahiye - kuch aur reject hota hai (UNKNOWN_VARIABLE) silently
 * ignore hone ke bajaye, kyunki ek typo'd variable name silently kuch
 * na karna ek real error se kahin zyada confusing hota; (3) har declared
 * variable jo {@code required=true} marked hai uske paas ek non-blank
 * value supplied honi chahiye - missing/null/empty-string sab missing
 * count hote hain (MISSING_VARIABLE); (4) ek {@code required=false}
 * marked declared variable jiske liye koi value supplied nahi hui wo
 * empty string ke roop me substitute hoti hai - wahi ek
 * jaan-boojh-kar-designed exception jo Step 10 allow karta hai ("missing
 * values ko silently replace karne se bacho... jab tak explicitly
 * designed na ho" - yehi wo explicit design hai, PromptVariable ka
 * javadoc dekho data-model side se wahi statement ke liye).
 *
 * Ye kyu hai: Phase 3.2 brief ka Step 9/10, aur wo actual "Rendered
 * Prompt" deliverable jo PAYMENTX_PHASE_3_ARCHITECTURE.md §4 Prompt
 * Service ko produce karte hue describe karta hai.
 * Dusre components se kaise communicate karta hai: sirf
 * PromptServiceImpl.renderPrompt dwara call hota hai, ek resolved
 * PromptVersion ke content+variables aur ek RenderPromptRequest ke
 * values map ke saath - kabhi ek controller dwara seedhe call nahi
 * hota, khud kabhi kisi doosre component ko call nahi karta.
 */
@Component
public class PromptRenderer {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z][a-zA-Z0-9_]*)\\s*}}");

    public record RenderResult(String renderedContent, List<String> variablesUsed) {
    }

    /**
     * Used both here (defense in depth) and by PromptServiceImpl at
     * prompt/version-creation time, so a template referencing an
     * undeclared {{placeholder}} is rejected immediately (INVALID_PROMPT_
     * CONTENT) rather than only failing the first time someone tries to
     * render it.
     */
    public void validateContentDeclaresOnlyKnownVariables(String content, List<PromptVariable> declaredVariables) {
        Set<String> declaredNames = declaredVariables.stream().map(PromptVariable::name).collect(java.util.stream.Collectors.toSet());
        for (String placeholder : extractPlaceholders(content)) {
            if (!declaredNames.contains(placeholder)) {
                throw new PaymentXException(PromptErrorCodes.INVALID_PROMPT_CONTENT,
                        "content references undeclared variable {{" + placeholder + "}}", false);
            }
        }
    }

    public RenderResult render(String content, List<PromptVariable> declaredVariables, Map<String, String> values) {
        Map<String, PromptVariable> declaredByName = declaredVariables.stream()
                .collect(java.util.stream.Collectors.toMap(PromptVariable::name, v -> v, (a, b) -> a, java.util.LinkedHashMap::new));

        validateContentDeclaresOnlyKnownVariables(content, declaredVariables);

        for (String suppliedName : values.keySet()) {
            if (!declaredByName.containsKey(suppliedName)) {
                throw new PaymentXException(PromptErrorCodes.UNKNOWN_VARIABLE,
                        "variable '" + suppliedName + "' is not declared for this prompt version", false);
            }
        }

        for (PromptVariable declared : declaredVariables) {
            String value = values.get(declared.name());
            boolean blank = value == null || value.isBlank();
            if (declared.required() && blank) {
                throw new PaymentXException(PromptErrorCodes.MISSING_VARIABLE,
                        "required variable '" + declared.name() + "' was not supplied", false);
            }
        }

        Set<String> variablesUsed = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = values.getOrDefault(name, "");
            variablesUsed.add(name);
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);

        return new RenderResult(rendered.toString(), List.copyOf(variablesUsed));
    }

    private Set<String> extractPlaceholders(String content) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }
}
