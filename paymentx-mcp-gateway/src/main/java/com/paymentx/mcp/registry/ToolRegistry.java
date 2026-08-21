package com.paymentx.mcp.registry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * English:
 * The central Tool Registry (Step 5) - collects every real
 * PaymentXTool Spring bean at startup (registration is explicit:
 * Spring's own dependency injection IS the "explicit registration"
 * mechanism Step 5 requires - there is no dynamic class loading, no
 * name-to-class reflection lookup anywhere in this module) and indexes
 * them by definition().name() for find-by-name lookup. Fails fast at
 * startup (IllegalStateException) if two tools ever register the same
 * name - a silent name collision would be a real security bug (which
 * tool actually executes?), so this is caught at boot, not discovered
 * later during a real tool call.
 * Why it exists: Step 5 - "register tools, discover tools, validate
 * tool metadata, find tool by name, check enabled/disabled state,
 * expose safe tool descriptions."
 * How it communicates with other components: constructor-injects
 * List&lt;PaymentXTool&gt; (every tool/ class Spring finds); ToolInvoker
 * calls findByName() before every real tool call;
 * config/McpServerConfig calls all() at startup to build the real MCP
 * tool list the SDK advertises; controller/McpToolCatalogController
 * calls all() to expose redacted descriptors over plain REST.
 *
 * Hinglish:
 * Ye central Tool Registry hai (Step 5) - startup par har real
 * PaymentXTool Spring bean collect karta hai (registration explicit
 * hai: Spring ki apni dependency injection hi wo "explicit
 * registration" mechanism hai jo Step 5 require karta hai - is module
 * me kahin bhi koi dynamic class loading, koi name-to-class reflection
 * lookup nahi hai) aur inhe definition().name() se index karta hai
 * find-by-name lookup ke liye. Startup par hi fail-fast hota hai
 * (IllegalStateException) agar do tools kabhi same naam register karte
 * hain - ek silent name collision ek real security bug hota (kaun sa
 * tool actually execute hota hai?), isliye ye boot par hi pakda jaata
 * hai, baad me ek real tool call ke dauraan discover nahi hota.
 * Ye kyu hai: Step 5 - "tools register karo, tools discover karo, tool
 * metadata validate karo, tool ko naam se dhoondho, enabled/disabled
 * state check karo, safe tool descriptions expose karo."
 * Dusre components se kaise communicate karta hai: List&lt;PaymentXTool&gt;
 * constructor-inject karta hai (tool/ ki har class jo Spring ko milti
 * hai); ToolInvoker har real tool call se pehle findByName() call karta
 * hai; config/McpServerConfig startup par all() call karta hai wo real
 * MCP tool list banane ke liye jo SDK advertise karta hai;
 * controller/McpToolCatalogController all() call karta hai redacted
 * descriptors plain REST par expose karne ke liye.
 */
@Component
@Slf4j
public class ToolRegistry {

    private final Map<String, PaymentXTool> toolsByName = new LinkedHashMap<>();

    public ToolRegistry(List<PaymentXTool> tools) {
        for (PaymentXTool tool : tools) {
            String name = tool.definition().name();
            if (toolsByName.containsKey(name)) {
                throw new IllegalStateException("Duplicate MCP tool name registered: " + name);
            }
            toolsByName.put(name, tool);
            log.info("Registered MCP tool name={} riskLevel={} readWrite={} enabled={}",
                    name, tool.definition().riskLevel(), tool.definition().readWrite(), tool.definition().enabled());
        }
    }

    public Optional<PaymentXTool> findByName(String name) {
        return Optional.ofNullable(toolsByName.get(name));
    }

    public List<PaymentXTool> all() {
        return List.copyOf(toolsByName.values());
    }
}
