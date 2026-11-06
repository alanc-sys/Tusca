package com.rutatur.match.service;

import com.rutatur.common.dto.MatchDto;
import com.rutatur.match.persistence.RouteMatchRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inbox: only the caller's sent and received matches, newest first. */
@Service
public class MatchQueryService {

    private final RouteMatchRepository routeMatchRepository;
    private final MatchMapper mapper;

    public MatchQueryService(RouteMatchRepository routeMatchRepository, MatchMapper mapper) {
        this.routeMatchRepository = routeMatchRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<MatchDto> inbox(String userId) {
        return routeMatchRepository
                .findByRequesterUserIdOrResponderUserIdOrderByRequestedAtDesc(userId, userId)
                .stream()
                .map(mapper::toDto)
                .toList();
    }
}
