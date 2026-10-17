package com.akamai.miniwsa.generator;

import com.akamai.miniwsa.dto.EventIngestRequest;
import com.akamai.miniwsa.dto.EventIngestRequest.GeoLocationRequest;
import com.akamai.miniwsa.dto.EventIngestRequest.RuleRequest;
import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.RuleCategory;
import com.akamai.miniwsa.model.Severity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Seeded DLR generator with no Spring types. Same {@code seed} and {@code end} are byte-identical.
 */
public final class EventGenerator {

    public static final int MIN_COUNT = 148;
    public static final int MAX_COUNT = 100_000;
    public static final int DEFAULT_COUNT = 10_000;
    public static final long DEFAULT_SEED = 42L;
    public static final Duration MIN_SPAN = Duration.ofMinutes(69);
    public static final Duration MAX_SPAN = Duration.ofDays(31);
    public static final Duration DEFAULT_SPAN = Duration.ofHours(24);

    public static final String SHOWCASE_RETURNER_A = "203.0.113.10";
    public static final String SHOWCASE_ONESHOT = "203.0.113.11";
    public static final String SHOWCASE_RETURNER_B = "203.0.113.12";
    public static final String PATH_ADMIN_USERS = "/admin/users";
    public static final String PATH_API_LOGIN = "/api/v1/login";
    public static final int SHOWCASE_CONFIG = 14227;

    private static final Duration MIN_BURST = Duration.ofSeconds(30);
    private static final Duration MAX_BURST = Duration.ofMinutes(3);
    private static final Duration MIN_GAP = Duration.ofMinutes(30);
    private static final Duration MAX_GAP = Duration.ofMinutes(90);

    private static final int IPV4_ORDINARY_CAP = 253;
    private static final Pattern COMPACT_SPAN = Pattern.compile("^(\\d+)([smhd])$");

    private static final RuleCategory[] CATEGORIES = RuleCategory.values();
    private static final int[] CATEGORY_WEIGHTS = {30, 20, 15, 10, 12, 8, 5};
    private static final Action[] ACTIONS = {Action.DENY, Action.ALERT, Action.MONITOR};
    private static final int[] ACTION_WEIGHTS = {50, 30, 20};
    private static final Severity[] SEVERITIES = {Severity.LOW, Severity.MEDIUM, Severity.HIGH, Severity.CRITICAL};
    private static final int[] SEVERITY_WEIGHTS = {30, 35, 25, 10};
    private static final int[] CONFIGS = {14227, 18350, 20911, 31002};
    private static final int[] CONFIG_WEIGHTS = {50, 25, 15, 10};

    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)",
        "Mozilla/5.0 (X11; Linux x86_64)",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)"
    };
    private static final GeoLocationRequest[] GEOS = {
        new GeoLocationRequest("CN", "Beijing"),
        new GeoLocationRequest("US", "New York"),
        new GeoLocationRequest("DE", "Frankfurt"),
        new GeoLocationRequest("BR", "Sao Paulo")
    };

    private EventGenerator() {}

    public record Result(List<EventIngestRequest> events, List<String> summaryLines) {}

    public static final class GeneratorException extends RuntimeException {
        public GeneratorException(String message) {
            super(message);
        }

        public GeneratorException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static Result generate(int count, long seed, Duration span, Instant end) {
        validate(count, span, end);
        var rng = new Random(seed);
        var windowStart = end.minus(span);
        var waves = planWaves(count, rng);
        int seq = 0;
        var raw = new ArrayList<RawEvent>(count);
        for (var wave : waves) {
            seq = emitCampaign(wave, rng, windowStart, span, end, seq, raw);
        }
        int remaining = count - raw.size();
        seq = emitBackground(remaining, rng, windowStart, span, seq, raw);
        if (raw.size() != count) {
            throw new IllegalStateException("generated %d events, expected %d".formatted(raw.size(), count));
        }
        raw.sort(Comparator.comparing(RawEvent::timestamp).thenComparingInt(RawEvent::seq));
        var events = new ArrayList<EventIngestRequest>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            events.add(toRequest(raw.get(i), eventId(seed, i)));
        }
        return new Result(List.copyOf(events), List.copyOf(summaryLines(waves)));
    }

    public static void validate(int count, Duration span, Instant end) {
        if (count < MIN_COUNT || count > MAX_COUNT) {
            throw new GeneratorException("count must be between %d and %d".formatted(MIN_COUNT, MAX_COUNT));
        }
        if (span == null || span.compareTo(MIN_SPAN) < 0 || span.compareTo(MAX_SPAN) > 0) {
            throw new GeneratorException("span must be between 69m and 31d");
        }
        if (end == null || end.isAfter(Instant.now())) {
            throw new GeneratorException("end must not be in the future");
        }
    }

    public static Duration parseSpan(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new GeneratorException("span must be a duration like 24h, 69m, 31d, or PT24H");
        }
        var trimmed = raw.trim();
        Matcher compact = COMPACT_SPAN.matcher(trimmed.toLowerCase(Locale.ROOT));
        if (compact.matches()) {
            try {
                long amount = Long.parseLong(compact.group(1));
                return switch (compact.group(2)) {
                    case "s" -> Duration.ofSeconds(amount);
                    case "m" -> Duration.ofMinutes(amount);
                    case "h" -> Duration.ofHours(amount);
                    case "d" -> Duration.ofDays(amount);
                    default -> throw new GeneratorException("span must be a duration like 24h, 69m, 31d, or PT24H");
                };
            } catch (NumberFormatException | ArithmeticException ex) {
                throw new GeneratorException("span must be a duration like 24h, 69m, 31d, or PT24H");
            }
        }
        try {
            return Duration.parse(trimmed);
        } catch (RuntimeException ex) {
            throw new GeneratorException("span must be a duration like 24h, 69m, 31d, or PT24H");
        }
    }

    public static ObjectMapper jsonMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public static String eventId(long seed, int index) {
        return "evt-s%d-%06d".formatted(seed, index);
    }

    public static void main(String[] args) {
        var err = System.err;
        try {
            var parsed = parseArgs(args);
            err.printf(
                    "Generating %d events (seed=%d, span=%s)%n",
                    parsed.count, parsed.seed, parsed.span);
            var result = generate(parsed.count, parsed.seed, parsed.span, parsed.end);
            var json = jsonMapper().writeValueAsString(result.events());
            if (parsed.output == null) {
                System.out.print(json);
            } else {
                Files.writeString(Path.of(parsed.output), json);
                err.printf("Wrote %s%n", parsed.output);
            }
            err.println("Summary:");
            for (var line : result.summaryLines()) {
                err.println(line);
            }
        } catch (GeneratorException
                | JsonProcessingException
                | IllegalArgumentException
                | java.time.DateTimeException ex) {
            err.println(ex.getMessage());
            System.exit(1);
        } catch (IOException ex) {
            err.println("Failed to write output: " + ex.getMessage());
            System.exit(1);
        }
    }

    static CliArgs parseArgs(String[] args) {
        int count = DEFAULT_COUNT;
        long seed = DEFAULT_SEED;
        Duration span = DEFAULT_SPAN;
        Instant end = null;
        String output = null;
        for (int i = 0; i < args.length; i++) {
            var arg = args[i];
            switch (arg) {
                case "--count" -> count = Integer.parseInt(requireValue(args, ++i, arg));
                case "--seed" -> seed = Long.parseLong(requireValue(args, ++i, arg));
                case "--span" -> span = parseSpan(requireValue(args, ++i, arg));
                case "--end" -> end = Instant.parse(requireValue(args, ++i, arg));
                case "-o", "--output" -> output = requireValue(args, ++i, arg);
                default -> throw new GeneratorException("Unknown argument: " + arg);
            }
        }
        if (end == null) {
            end = Instant.now();
        }
        return new CliArgs(count, seed, span, end, output);
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new GeneratorException(flag + " requires a value");
        }
        return args[index];
    }

    record CliArgs(int count, long seed, Duration span, Instant end, String output) {}

    private static List<Wave> planWaves(int count, Random rng) {
        int waveCount = Math.max(10, count / 200);
        int returners = Math.max(2, (int) Math.round(0.2 * waveCount));
        int ordinary = waveCount - 3;
        int ordinaryReturners = Math.min(ordinary, Math.max(0, returners - 2));

        var waves = new ArrayList<Wave>(waveCount);
        waves.add(new Wave(
                SHOWCASE_RETURNER_A,
                PATH_ADMIN_USERS,
                RuleCategory.XSS,
                Severity.CRITICAL,
                Action.DENY,
                SHOWCASE_CONFIG,
                new int[] {20, 20},
                true));
        waves.add(new Wave(
                SHOWCASE_ONESHOT,
                PATH_API_LOGIN,
                RuleCategory.INJECTION,
                Severity.CRITICAL,
                Action.DENY,
                SHOWCASE_CONFIG,
                new int[] {36},
                false));
        waves.add(new Wave(
                SHOWCASE_RETURNER_B,
                PATH_API_LOGIN,
                RuleCategory.INJECTION,
                Severity.CRITICAL,
                Action.DENY,
                SHOWCASE_CONFIG,
                new int[] {15, 15},
                true));

        var burstCounts = new int[ordinary];
        int remaining = count - 106;
        for (int i = 0; i < ordinary; i++) {
            boolean returning = i < ordinaryReturners;
            burstCounts[i] = returning ? 2 + rng.nextInt(2) : 1;
            remaining -= burstCounts[i] * 6;
        }
        if (remaining < 0) {
            throw new IllegalStateException("wave minimum exceeded count");
        }

        int ordinaryIndex = 0;
        for (int i = 0; i < ordinary; i++) {
            boolean returning = i < ordinaryReturners;
            int bursts = burstCounts[i];
            int maxExtra = returning ? 2 : 8;
            var sizes = new int[bursts];
            for (int b = 0; b < bursts; b++) {
                int extraCap = Math.min(maxExtra, remaining);
                int extra = extraCap <= 0 ? 0 : rng.nextInt(extraCap + 1);
                remaining -= extra;
                sizes[b] = 6 + extra;
            }
            waves.add(new Wave(
                    ordinaryAddress(ordinaryIndex),
                    ordinaryPath(ordinaryIndex),
                    pick(rng, CATEGORIES, CATEGORY_WEIGHTS),
                    pick(rng, SEVERITIES, SEVERITY_WEIGHTS),
                    pick(rng, ACTIONS, ACTION_WEIGHTS),
                    pick(rng, CONFIGS, CONFIG_WEIGHTS),
                    sizes,
                    returning));
            ordinaryIndex++;
        }
        return waves;
    }

    private static int emitCampaign(
            Wave wave,
            Random rng,
            Instant windowStart,
            Duration span,
            Instant end,
            int seq,
            List<RawEvent> out) {
        int bursts = wave.burstSizes.length;
        var durations = new long[bursts];
        long minBurstMs = MIN_BURST.toMillis();
        long maxBurstMs = MAX_BURST.toMillis();
        long burstRange = maxBurstMs - minBurstMs + 1;
        for (int i = 0; i < bursts; i++) {
            durations[i] = minBurstMs + rng.nextLong(burstRange);
        }
        long minGapMs = MIN_GAP.toMillis();
        long extraGapCap = MAX_GAP.minus(MIN_GAP).toMillis();
        long minTotal = 0;
        for (long duration : durations) {
            minTotal += duration;
        }
        minTotal += (long) (bursts - 1) * minGapMs;
        long slack = span.toMillis() - minTotal;
        if (slack < 0) {
            throw new GeneratorException("span is too short to place a campaign");
        }
        var gaps = new long[Math.max(0, bursts - 1)];
        for (int i = 0; i < gaps.length; i++) {
            long cap = Math.min(extraGapCap, slack);
            long extra = cap == 0 ? 0 : rng.nextLong(cap + 1);
            gaps[i] = minGapMs + extra;
            slack -= extra;
        }
        long startOffset = slack == 0 ? 0 : rng.nextLong(slack + 1);
        long cursor = startOffset;
        for (int b = 0; b < bursts; b++) {
            seq = emitBurst(wave, wave.burstSizes[b], windowStart.plusMillis(cursor), durations[b], end, rng, seq, out);
            cursor += durations[b];
            if (b < gaps.length) {
                cursor += gaps[b];
            }
        }
        return seq;
    }

    private static int emitBurst(
            Wave wave,
            int size,
            Instant burstStart,
            long durationMs,
            Instant end,
            Random rng,
            int seq,
            List<RawEvent> out) {
        var duration = Duration.ofMillis(durationMs);
        for (int i = 0; i < size; i++) {
            Instant ts = size == 1 || durationMs == 0
                    ? burstStart
                    : burstStart.plus(duration.multipliedBy(i).dividedBy(size - 1L));
            if (!ts.isBefore(end)) {
                ts = end.minusNanos(1);
            }
            out.add(decorate(wave, ts, seq++, rng));
        }
        return seq;
    }

    private static int emitBackground(
            int remaining, Random rng, Instant windowStart, Duration span, int seq, List<RawEvent> out) {
        if (remaining == 0) {
            return seq;
        }
        int pool = (remaining + 1) / 2;
        var counts = new int[pool];
        java.util.Arrays.fill(counts, 1);
        int extras = remaining - pool;
        var eligible = new ArrayList<Integer>(pool);
        for (int i = 0; i < pool; i++) {
            eligible.add(i);
        }
        for (int e = 0; e < extras; e++) {
            int pick = rng.nextInt(eligible.size());
            int ipIndex = eligible.get(pick);
            counts[ipIndex]++;
            if (counts[ipIndex] == 4) {
                eligible.remove(pick);
            }
        }
        long spanMs = span.toMillis();
        for (int i = 0; i < pool; i++) {
            String ip = backgroundAddress(i);
            String path = "/static/asset-" + i;
            for (int n = 0; n < counts[i]; n++) {
                Instant ts = windowStart.plusMillis(spanMs == 0 ? 0 : rng.nextLong(spanMs));
                var category = pick(rng, CATEGORIES, CATEGORY_WEIGHTS);
                var severity = pick(rng, SEVERITIES, SEVERITY_WEIGHTS);
                var action = pick(rng, ACTIONS, ACTION_WEIGHTS);
                int configId = pick(rng, CONFIGS, CONFIG_WEIGHTS);
                out.add(decorate(
                        new Wave(ip, path, category, severity, action, configId, new int[] {counts[i]}, false),
                        ts,
                        seq++,
                        rng));
            }
        }
        return seq;
    }

    private static RawEvent decorate(Wave wave, Instant timestamp, int seq, Random rng) {
        boolean reservedPath = PATH_ADMIN_USERS.equals(wave.path) || PATH_API_LOGIN.equals(wave.path);
        String method = reservedPath ? "POST" : "GET";
        int status = wave.action == Action.DENY ? 403 : 200;
        String userAgent = USER_AGENTS[rng.nextInt(USER_AGENTS.length)];
        var geo = GEOS[rng.nextInt(GEOS.length)];
        int requestSize = 256 + rng.nextInt(4096 - 256 + 1);
        int responseSize = 64 + rng.nextInt(2048 - 64 + 1);
        return new RawEvent(
                timestamp,
                seq,
                wave.configId,
                wave.ip,
                wave.path,
                method,
                status,
                userAgent,
                ruleFor(wave.category, wave.severity),
                wave.action,
                geo,
                requestSize,
                responseSize);
    }

    private static EventIngestRequest toRequest(RawEvent raw, String eventId) {
        return new EventIngestRequest(
                eventId,
                raw.timestamp,
                raw.configId,
                "pol_web1",
                raw.clientIp,
                "www.example.com",
                raw.path,
                raw.method,
                raw.statusCode,
                raw.userAgent,
                raw.rule,
                raw.action,
                raw.geo,
                raw.requestSize,
                raw.responseSize);
    }

    private static RuleRequest ruleFor(RuleCategory category, Severity severity) {
        return switch (category) {
            case INJECTION -> new RuleRequest("950001", "SQL_INJECTION", "SQL Injection Attack Detected", severity, category);
            case XSS -> new RuleRequest("950002", "XSS", "Cross-Site Scripting Attack Detected", severity, category);
            case PROTOCOL_VIOLATION -> new RuleRequest(
                    "950003", "PROTOCOL_VIOLATION", "Protocol Anomaly Detected", severity, category);
            case DATA_LEAKAGE -> new RuleRequest(
                    "950004", "DATA_LEAKAGE", "Data Exfiltration Detected", severity, category);
            case BOT -> new RuleRequest("950005", "BOT", "Bot Activity Detected", severity, category);
            case DOS -> new RuleRequest("950006", "DOS", "Denial of Service Attack Detected", severity, category);
            case RATE_LIMIT -> new RuleRequest("950007", "RATE_LIMIT", "Rate Limiting Triggered", severity, category);
        };
    }

    private static List<String> summaryLines(List<Wave> waves) {
        var lines = new ArrayList<String>();
        for (var wave : waves) {
            if (wave.showcase() || wave.returning) {
                int total = 0;
                for (int size : wave.burstSizes) {
                    total += size;
                }
                lines.add("%s %s ×%d bursts, %d events"
                        .formatted(wave.ip, wave.path, wave.burstSizes.length, total));
            }
        }
        return lines;
    }

    static String ordinaryAddress(int ordinaryIndex) {
        if (ordinaryIndex < IPV4_ORDINARY_CAP) {
            int octet = ordinaryIndex < 10 ? ordinaryIndex : ordinaryIndex + 3;
            return "203.0.113." + octet;
        }
        return "2001:db8:1::" + Integer.toHexString(ordinaryIndex - IPV4_ORDINARY_CAP + 1);
    }

    static String backgroundAddress(int index) {
        return "2001:db8:2::" + Integer.toHexString(index + 1);
    }

    private static String ordinaryPath(int ordinaryIndex) {
        if (ordinaryIndex % 4 == 0) {
            return (ordinaryIndex / 4) % 2 == 0
                    ? "/login/reset/" + ordinaryIndex
                    : "/admin/audit/" + ordinaryIndex;
        }
        return "/api/v1/items/" + ordinaryIndex;
    }

    private static <T> T pick(Random rng, T[] values, int[] weights) {
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        int roll = rng.nextInt(total);
        int acc = 0;
        for (int i = 0; i < values.length; i++) {
            acc += weights[i];
            if (roll < acc) {
                return values[i];
            }
        }
        return values[values.length - 1];
    }

    private static int pick(Random rng, int[] values, int[] weights) {
        Integer[] boxed = new Integer[values.length];
        for (int i = 0; i < values.length; i++) {
            boxed[i] = values[i];
        }
        return pick(rng, boxed, weights);
    }

    private record Wave(
            String ip,
            String path,
            RuleCategory category,
            Severity severity,
            Action action,
            int configId,
            int[] burstSizes,
            boolean returning) {
        boolean showcase() {
            return SHOWCASE_RETURNER_A.equals(ip)
                    || SHOWCASE_ONESHOT.equals(ip)
                    || SHOWCASE_RETURNER_B.equals(ip);
        }
    }

    private record RawEvent(
            Instant timestamp,
            int seq,
            int configId,
            String clientIp,
            String path,
            String method,
            int statusCode,
            String userAgent,
            RuleRequest rule,
            Action action,
            GeoLocationRequest geo,
            int requestSize,
            int responseSize) {}
}
