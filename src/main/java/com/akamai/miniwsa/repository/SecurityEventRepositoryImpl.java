package com.akamai.miniwsa.repository;

import com.akamai.miniwsa.model.SecurityEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
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
}
