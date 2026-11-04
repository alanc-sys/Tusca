package com.rutatur.match.web;

import com.rutatur.common.dto.ShareableItineraryDto;
import com.rutatur.common.dto.SharedRouteDto;
import com.rutatur.common.dto.ShareRouteRequest;
import com.rutatur.match.service.ShareService;
import com.rutatur.match.service.ShareableQueryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Opt-in sharing surface. Ownership always comes from the JWT subject. */
@RestController
@RequestMapping("/api/routes")
@PreAuthorize("hasRole('USER')")
public class RouteShareController {

    private final ShareService shareService;
    private final ShareableQueryService shareableQueryService;

    public RouteShareController(
            ShareService shareService, ShareableQueryService shareableQueryService) {
        this.shareService = shareService;
        this.shareableQueryService = shareableQueryService;
    }

    @PostMapping("/share")
    public ResponseEntity<SharedRouteDto> share(
            @Valid @RequestBody ShareRouteRequest request, @AuthenticationPrincipal Jwt jwt) {
        ShareService.ShareResult result = shareService.share(request.itineraryId(), jwt.getSubject());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.dto());
    }

    @GetMapping("/shareable")
    public List<ShareableItineraryDto> shareable(@AuthenticationPrincipal Jwt jwt) {
        return shareableQueryService.shareable(jwt.getSubject());
    }
}
