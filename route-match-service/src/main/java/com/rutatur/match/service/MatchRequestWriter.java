package com.rutatur.match.service;

import com.rutatur.match.persistence.RouteMatchEntity;
import com.rutatur.match.persistence.RouteMatchRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolates the insert in its own transaction so a duplicate-key race rolls back only this write.
 * The request service can then re-read the winner without inheriting an aborted transaction.
 */
@Component
public class MatchRequestWriter {

    private final RouteMatchRepository routeMatchRepository;

    public MatchRequestWriter(RouteMatchRepository routeMatchRepository) {
        this.routeMatchRepository = routeMatchRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RouteMatchEntity insert(RouteMatchEntity entity) {
        return routeMatchRepository.saveAndFlush(entity);
    }
}
