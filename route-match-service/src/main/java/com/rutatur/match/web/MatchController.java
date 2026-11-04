package com.rutatur.match.web;

import com.rutatur.common.dto.MatchDto;
import com.rutatur.common.dto.MatchRequestDto;
import com.rutatur.match.service.MatchLifecycleService;
import com.rutatur.match.service.MatchQueryService;
import com.rutatur.match.service.MatchRequestService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Request, inbox, accept and reject; participants only. */
@RestController
@RequestMapping("/api/routes/matches")
@PreAuthorize("hasRole('USER')")
public class MatchController {

    private final MatchRequestService requestService;
    private final MatchLifecycleService lifecycleService;
    private final MatchQueryService queryService;

    public MatchController(
            MatchRequestService requestService,
            MatchLifecycleService lifecycleService,
            MatchQueryService queryService) {
        this.requestService = requestService;
        this.lifecycleService = lifecycleService;
        this.queryService = queryService;
    }

    @PostMapping("/request")
    public ResponseEntity<MatchDto> request(
            @Valid @RequestBody MatchRequestDto request, @AuthenticationPrincipal Jwt jwt) {
        MatchRequestService.RequestResult result =
                requestService.request(jwt.getSubject(), request.targetRouteId());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.dto());
    }

    @GetMapping
    public List<MatchDto> inbox(@AuthenticationPrincipal Jwt jwt) {
        return queryService.inbox(jwt.getSubject());
    }

    @PostMapping("/{matchId}/accept")
    public MatchDto accept(@PathVariable UUID matchId, @AuthenticationPrincipal Jwt jwt) {
        return lifecycleService.accept(matchId, jwt.getSubject());
    }

    @PostMapping("/{matchId}/reject")
    public MatchDto reject(@PathVariable UUID matchId, @AuthenticationPrincipal Jwt jwt) {
        return lifecycleService.reject(matchId, jwt.getSubject());
    }
}
