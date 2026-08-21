package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.config.ControlCenterProperties;
import com.paymentx.controlcenter.dto.logs.LogEntry;
import com.paymentx.controlcenter.exception.ControlCenterException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ENGLISH: The entire safety boundary and parser for the Phase 4 Log
 * Viewer - reads ONLY from real svc-&lt;service&gt;.log files inside the
 * one explicitly-configured directory (ControlCenterProperties.Logs),
 * never a caller-supplied path (the {service} filter is validated
 * against a fixed allowlist, exactly the same "server-configured
 * allowlist, no browser-supplied path" pattern SafeFileService uses
 * for the Files feature). What it does: tails each real log file
 * (bounded to maxLinesScannedPerFile raw lines - "do not load
 * unlimited logs"), parses each line against the real Spring Boot
 * pattern every service's application.yml already uses, groups
 * unparsed continuation lines (stack traces) into the previous real
 * entry's message instead of inventing a fake structured row for
 * them, applies every real filter, and returns at most
 * maxEntriesReturned entries sorted newest-first. Why it exists: the
 * Phase 4 brief's real, bounded, filterable log viewer requirement -
 * satisfied against the platform's actual redirected stdout, not a
 * simulated log line.
 *
 * HINGLISH: Phase 4 Log Viewer ke liye poori safety boundary aur
 * parser - sirf us ek explicitly-configured directory ke andar ke
 * real svc-&lt;service&gt;.log files se padhta hai
 * (ControlCenterProperties.Logs), kabhi caller-supplied path nahi
 * ({service} filter ek fixed allowlist ke against validate hota hai,
 * bilkul wahi "server-configured allowlist, browser-supplied path
 * nahi" pattern jo SafeFileService Files feature ke liye use karta
 * hai). Ye kya karti hai: har real log file tail karta hai
 * (maxLinesScannedPerFile raw lines tak bound - "unlimited logs load
 * mat karo"), har line ko us real Spring Boot pattern ke against parse
 * karta hai jo har service ka application.yml already use karta hai,
 * unparsed continuation lines (stack traces) ko pichli real entry ke
 * message me group karta hai unke liye ek fake structured row invent
 * karne ke bajaye, har real filter apply karta hai, aur zyada se zyada
 * maxEntriesReturned entries newest-first sorted return karta hai. Ye
 * dashboard me kyu hai: Phase 4 brief ka real, bounded, filterable log
 * viewer requirement - platform ke actual redirected stdout ke against
 * satisfy kiya gaya, ek simulated log line nahi.
 */
@Service
public class LogFileService {

    /** Fixed allowlist mirroring paymentx-validation-suite/lib/common.ps1's $Script:Services keys and its real svc-*.log naming. */
    private static final Set<String> KNOWN_SERVICES = new LinkedHashSet<>(List.of(
            "api-gateway", "auth-service", "validation-service", "payment-service", "routing-service",
            "audit-service", "notification-service", "reconciliation-service", "reporting-service"));

    // Real Spring Boot startup line shape: "<ts>  <LEVEL> [correlationId=.. traceId=.. paymentId=.. participantId=..] <pid> --- ..."
    private static final Pattern TS_LEVEL = Pattern.compile("^(\\S+)\\s+(TRACE|DEBUG|INFO|WARN|ERROR)\\s+(.*)$");
    private static final Pattern MDC_BLOCK = Pattern.compile(
            "^\\[correlationId=([^\\s\\]]*)\\s+traceId=([^\\s\\]]*)\\s+paymentId=([^\\s\\]]*)\\s+participantId=([^\\]]*)]\\s*(.*)$");
    private static final Pattern SIMPLE_CORRELATION = Pattern.compile("^\\[([^\\]]*)]\\s*(.*)$");

    private final Path logsDirectory;
    private final int maxLinesScannedPerFile;
    private final int maxEntriesReturned;

    public LogFileService(ControlCenterProperties properties) {
        this.logsDirectory = Path.of(properties.getLogs().getOutputDirectory()).toAbsolutePath().normalize();
        this.maxLinesScannedPerFile = properties.getLogs().getMaxLinesScannedPerFile();
        this.maxEntriesReturned = properties.getLogs().getMaxEntriesReturned();
    }

    public List<String> knownServices() {
        return List.copyOf(KNOWN_SERVICES);
    }

    public List<LogEntry> query(String service, String level, String correlationId, String traceId,
                                 String paymentReference, String search, OffsetDateTime from, OffsetDateTime to,
                                 Integer limit) {
        if (service != null && !service.isBlank() && !KNOWN_SERVICES.contains(service)) {
            throw new ControlCenterException("UNKNOWN_SERVICE", "Unknown service: " + service);
        }
        int effectiveLimit = limit == null ? 200 : Math.max(1, Math.min(limit, maxEntriesReturned));

        List<String> servicesToRead = service != null && !service.isBlank() ? List.of(service) : List.copyOf(KNOWN_SERVICES);
        List<LogEntry> matched = new ArrayList<>();
        for (String svc : servicesToRead) {
            for (LogEntry entry : readAndParse(svc)) {
                if (matches(entry, level, correlationId, traceId, paymentReference, search, from, to)) {
                    matched.add(entry);
                }
            }
        }
        matched.sort(Comparator.comparing(LogEntry::timestamp, Comparator.nullsLast(Comparator.reverseOrder())));
        return matched.size() > effectiveLimit ? matched.subList(0, effectiveLimit) : matched;
    }

    /** Real, bounded tail read (last maxLinesScannedPerFile raw lines) of one real service's real log file - a missing file is a real, honest empty result, never fabricated. */
    private List<String> tailLines(String service) {
        Path file = logsDirectory.resolve("svc-" + service + ".log").normalize();
        if (!file.startsWith(logsDirectory) || !Files.isRegularFile(file)) {
            return List.of();
        }
        Deque<String> window = new ArrayDeque<>(maxLinesScannedPerFile);
        try (var lines = Files.lines(file, java.nio.charset.StandardCharsets.UTF_8)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (window.size() == maxLinesScannedPerFile) {
                    window.removeFirst();
                }
                window.addLast(line);
            }
        } catch (IOException e) {
            throw new ControlCenterException("LOGS_UNREADABLE", "Could not read log file for " + service + ": " + e.getMessage(), e);
        }
        return new ArrayList<>(window);
    }

    private List<LogEntry> readAndParse(String service) {
        List<String> rawLines = tailLines(service);
        List<LogEntry> entries = new ArrayList<>();
        for (String line : rawLines) {
            LogEntry parsed = parseLine(service, line);
            if (parsed != null) {
                entries.add(parsed);
            } else if (!entries.isEmpty() && !line.isBlank()) {
                // A real continuation line (e.g. one stack-trace frame) - folded into the previous real entry's message.
                LogEntry previous = entries.remove(entries.size() - 1);
                entries.add(new LogEntry(previous.service(), previous.timestamp(), previous.level(),
                        previous.correlationId(), previous.traceId(), previous.paymentReference(),
                        previous.participantId(), previous.logger(), previous.message() + "\n" + line, previous.structured()));
            } else if (!line.isBlank()) {
                entries.add(new LogEntry(service, null, "UNKNOWN", null, null, null, null, null, line, false));
            }
        }
        return entries;
    }

    private LogEntry parseLine(String service, String line) {
        Matcher tsLevel = TS_LEVEL.matcher(line);
        if (!tsLevel.matches()) {
            return null;
        }
        OffsetDateTime timestamp = parseTimestamp(tsLevel.group(1));
        String level = tsLevel.group(2);
        String remainder = tsLevel.group(3);

        String correlationId = null, traceId = null, paymentReference = null, participantId = null;
        Matcher mdc = MDC_BLOCK.matcher(remainder);
        if (mdc.matches()) {
            correlationId = blankToNull(mdc.group(1));
            traceId = blankToNull(mdc.group(2));
            paymentReference = blankToNull(mdc.group(3));
            participantId = blankToNull(mdc.group(4));
            remainder = mdc.group(5);
        } else {
            Matcher simple = SIMPLE_CORRELATION.matcher(remainder);
            if (simple.matches()) {
                correlationId = blankToNull(simple.group(1));
                remainder = simple.group(2);
            }
        }

        String logger = null;
        String message = remainder;
        int separator = remainder.lastIndexOf(" : ");
        if (separator >= 0) {
            logger = remainder.substring(0, separator).trim();
            // Real Spring Boot pattern still has "pid --- [service] [thread] [span] logger" before the logger name -
            // keep only the last whitespace-separated token of that prefix as the actual logger class name.
            int lastSpace = logger.lastIndexOf(' ');
            if (lastSpace >= 0) {
                logger = logger.substring(lastSpace + 1);
            }
            message = remainder.substring(separator + 3);
        }

        return new LogEntry(service, timestamp, level, correlationId, traceId, paymentReference, participantId, logger, message, true);
    }

    private OffsetDateTime parseTimestamp(String raw) {
        try {
            return OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean matches(LogEntry entry, String level, String correlationId, String traceId,
                             String paymentReference, String search, OffsetDateTime from, OffsetDateTime to) {
        if (level != null && !level.isBlank() && !level.equalsIgnoreCase(entry.level())) return false;
        if (correlationId != null && !correlationId.isBlank()
                && (entry.correlationId() == null || !entry.correlationId().toLowerCase().contains(correlationId.toLowerCase()))) return false;
        if (traceId != null && !traceId.isBlank()
                && (entry.traceId() == null || !entry.traceId().toLowerCase().contains(traceId.toLowerCase()))) return false;
        if (paymentReference != null && !paymentReference.isBlank()
                && (entry.paymentReference() == null || !entry.paymentReference().toLowerCase().contains(paymentReference.toLowerCase()))) return false;
        if (from != null && (entry.timestamp() == null || entry.timestamp().isBefore(from))) return false;
        if (to != null && (entry.timestamp() == null || entry.timestamp().isAfter(to))) return false;
        if (search != null && !search.isBlank()) {
            String needle = search.toLowerCase();
            boolean inMessage = entry.message() != null && entry.message().toLowerCase().contains(needle);
            boolean inLogger = entry.logger() != null && entry.logger().toLowerCase().contains(needle);
            if (!inMessage && !inLogger) return false;
        }
        return true;
    }
}
