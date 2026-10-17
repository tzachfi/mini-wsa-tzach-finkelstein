package com.akamai.miniwsa.repository;

import com.akamai.miniwsa.model.SecurityEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SecurityEventRepositoryImpl implements SecurityEventRepositoryCustom {

    private final EntityManager entityManager;

    @Override
    public List<Object[]> findTimestampsInWindows(List<IpTimeWindow> windows) {
        if (windows.isEmpty()) {
            return List.of();
        }

        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(Object[].class);
        var event = query.from(SecurityEvent.class);
        var predicates = windows.stream()
                .map(window -> cb.and(
                        cb.equal(event.get("clientIp"), window.clientIp()),
                        cb.between(event.get("timestamp"), window.from(), window.to())))
                .toArray(Predicate[]::new);

        query.multiselect(event.get("clientIp"), event.get("timestamp"));
        query.where(cb.or(predicates));
        query.orderBy(cb.asc(event.get("clientIp")), cb.asc(event.get("timestamp")));
        return entityManager.createQuery(query).getResultList();
    }

    @Override
    public List<SecurityEvent> findPage(SampleFilter filter, int offset, int limit) {
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(SecurityEvent.class);
        var event = query.from(SecurityEvent.class);
        query.select(event);
        applyFilter(cb, query, event, filter);
        query.orderBy(cb.desc(event.get("timestamp")), cb.desc(event.get("id")));
        return entityManager
                .createQuery(query)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public long countMatching(SampleFilter filter, int cap) {
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(Long.class);
        var event = query.from(SecurityEvent.class);
        query.select(event.get("id"));
        applyFilter(cb, query, event, filter);
        return entityManager.createQuery(query).setMaxResults(cap + 1).getResultList().size();
    }

    private static void applyFilter(
            CriteriaBuilder cb, CriteriaQuery<?> query, Root<SecurityEvent> event, SampleFilter filter) {
        var predicates = new ArrayList<Predicate>();
        if (filter.configId() != null) {
            predicates.add(cb.equal(event.get("configId"), filter.configId()));
        }
        if (filter.from() != null) {
            predicates.add(cb.greaterThanOrEqualTo(event.get("timestamp"), filter.from()));
        }
        if (filter.to() != null) {
            predicates.add(cb.lessThan(event.get("timestamp"), filter.to()));
        }
        if (filter.category() != null) {
            predicates.add(cb.equal(event.get("rule").get("category"), filter.category()));
        }
        if (filter.action() != null) {
            predicates.add(cb.equal(event.get("action"), filter.action()));
        }
        if (!predicates.isEmpty()) {
            query.where(predicates.toArray(Predicate[]::new));
        }
    }
}
