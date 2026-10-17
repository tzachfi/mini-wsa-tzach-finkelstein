package com.akamai.miniwsa.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akamai.miniwsa.dto.EventIngestRequest;
import com.akamai.miniwsa.enrichment.RepeatWindowCounter;
import com.akamai.miniwsa.enrichment.ThreatScoreCalculator;
import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.SecurityEvent;
import com.akamai.miniwsa.model.Severity;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventGeneratorTest {

    private static final Instant END = Instant.parse("2026-06-02T00:00:00Z");
    private static final Duration SPAN = Duration.ofHours(24);

    @Test
    void sameSeedAndEndAreByteIdentical() throws Exception {
        var first = EventGenerator.generate(EventGenerator.MIN_COUNT, 42L, SPAN, END);
        var second = EventGenerator.generate(EventGenerator.MIN_COUNT, 42L, SPAN, END);

        assertThat(first.events()).isEqualTo(second.events());
        assertThat(EventGenerator.jsonMapper().writeValueAsBytes(first.events()))
                .isEqualTo(EventGenerator.jsonMapper().writeValueAsBytes(second.events()));
    }

    @Test
    void showcaseCountsAndReservedPathTotalsHold() {
        var result = EventGenerator.generate(10_000, 7L, SPAN, END);

        assertThat(countFor(result, EventGenerator.SHOWCASE_RETURNER_A)).isEqualTo(40);
        assertThat(countFor(result, EventGenerator.SHOWCASE_ONESHOT)).isEqualTo(36);
        assertThat(countFor(result, EventGenerator.SHOWCASE_RETURNER_B)).isEqualTo(30);
        assertThat(pathCount(result, EventGenerator.PATH_API_LOGIN)).isEqualTo(66);
        assertThat(pathCount(result, EventGenerator.PATH_ADMIN_USERS)).isEqualTo(40);

        result.events().stream()
                .filter(event -> !isShowcase(event.clientIp()))
                .collect(java.util.stream.Collectors.groupingBy(EventIngestRequest::clientIp))
                .values()
                .forEach(group -> assertThat(group.size()).isLessThanOrEqualTo(24));
    }

    @Test
    void returningShowcaseBurstResetsRepeatBonus() {
        var result = EventGenerator.generate(10_000, 42L, SPAN, END);
        var ofIp = result.events().stream()
                .filter(event -> EventGenerator.SHOWCASE_RETURNER_A.equals(event.clientIp()))
                .toList();
        assertThat(ofIp).hasSize(40);

        int burst2Start = -1;
        for (int i = 1; i < ofIp.size(); i++) {
            if (Duration.between(ofIp.get(i - 1).timestamp(), ofIp.get(i).timestamp())
                            .compareTo(Duration.ofMinutes(30))
                    >= 0) {
                burst2Start = i;
                break;
            }
        }
        assertThat(burst2Start).isEqualTo(20);

        var securityEvents = ofIp.stream()
                .map(event -> SecurityEvent.builder()
                        .eventId(event.eventId())
                        .clientIp(event.clientIp())
                        .timestamp(event.timestamp())
                        .build())
                .toList();
        var priors = new RepeatWindowCounter().priorCounts(securityEvents, Map.of());
        var calculator = new ThreatScoreCalculator();

        assertThat(calculator.calculate(
                        Severity.CRITICAL, Action.DENY, EventGenerator.PATH_ADMIN_USERS, priors[burst2Start]))
                .isEqualTo(75);
        assertThat(calculator.calculate(
                        Severity.CRITICAL,
                        Action.DENY,
                        EventGenerator.PATH_ADMIN_USERS,
                        priors[burst2Start + 5]))
                .isEqualTo(90);
    }

    @Test
    void largeRunEmitsIpv6WaveAddressWithinColumnWidth() {
        var result = EventGenerator.generate(51_400, 1L, SPAN, END);

        assertThat(result.events().stream()
                        .map(EventIngestRequest::clientIp)
                        .filter(ip -> ip.startsWith("2001:db8:1::"))
                        .toList())
                .isNotEmpty()
                .allSatisfy(ip -> assertThat(ip.length()).isLessThanOrEqualTo(64));
    }

    @Test
    void eventIdsAreSeededAndPaddedAfterTimestampSort() {
        var result = EventGenerator.generate(EventGenerator.MIN_COUNT, 42L, SPAN, END);

        assertThat(result.events().getFirst().eventId()).isEqualTo("evt-s42-000000");
        assertThat(result.events().getLast().eventId()).isEqualTo("evt-s42-000147");
        for (int i = 1; i < result.events().size(); i++) {
            assertThat(result.events().get(i).timestamp())
                    .isAfterOrEqualTo(result.events().get(i - 1).timestamp());
        }
    }

    @Test
    void countBelowMinimumIsRejected() {
        assertThatThrownBy(() -> EventGenerator.generate(147, 42L, SPAN, END))
                .isInstanceOf(EventGenerator.GeneratorException.class)
                .hasMessageContaining("count");
    }

    @Test
    void parseSpanAcceptsCompactAndIsoForms() {
        assertThat(EventGenerator.parseSpan("24h")).isEqualTo(Duration.ofHours(24));
        assertThat(EventGenerator.parseSpan("69m")).isEqualTo(Duration.ofMinutes(69));
        assertThat(EventGenerator.parseSpan("31d")).isEqualTo(Duration.ofDays(31));
        assertThat(EventGenerator.parseSpan("PT24H")).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void parseSpanRejectsOverflowingCompactAndNonDurationText() {
        assertThatThrownBy(() -> EventGenerator.parseSpan("99999999999999999999h"))
                .isInstanceOf(EventGenerator.GeneratorException.class)
                .hasMessageContaining("span must be a duration");
        assertThatThrownBy(() -> EventGenerator.parseSpan("abc"))
                .isInstanceOf(EventGenerator.GeneratorException.class)
                .hasMessageContaining("span must be a duration");
    }

    private static long countFor(EventGenerator.Result result, String clientIp) {
        return result.events().stream().filter(event -> clientIp.equals(event.clientIp())).count();
    }

    private static long pathCount(EventGenerator.Result result, String path) {
        return result.events().stream().filter(event -> path.equals(event.path())).count();
    }

    private static boolean isShowcase(String clientIp) {
        return EventGenerator.SHOWCASE_RETURNER_A.equals(clientIp)
                || EventGenerator.SHOWCASE_ONESHOT.equals(clientIp)
                || EventGenerator.SHOWCASE_RETURNER_B.equals(clientIp);
    }
}
