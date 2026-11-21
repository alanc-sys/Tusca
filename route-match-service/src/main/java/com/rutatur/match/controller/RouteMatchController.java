package com.rutatur.match.controller;

import com.rutatur.common.dto.MatchRatingResponse;
import com.rutatur.common.dto.RateMatchRequest;
import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;
import com.rutatur.match.dto.MatchContactResponse;
import com.rutatur.match.dto.MatchRequestDto;
import com.rutatur.match.dto.MatchSuggestionResponse;
import com.rutatur.match.dto.RouteMatchResponse;
import com.rutatur.match.dto.ShareRouteRequest;
import com.rutatur.match.dto.SharedRouteResponse;
import com.rutatur.match.service.MatchRatingService;
import com.rutatur.match.service.RouteMatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/routes")
@Tag(name = "Route Match", description = "Publicación opt-in de rutas, sugerencias de afinidad y consentimiento bilateral")
public class RouteMatchController {

    private final RouteMatchService routeMatchService;
    private final MatchRatingService matchRatingService;

    public RouteMatchController(RouteMatchService routeMatchService, MatchRatingService matchRatingService) {
        this.routeMatchService = routeMatchService;
        this.matchRatingService = matchRatingService;
    }

    @PostMapping("/share")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publica un itinerario propio para matching (opt-in)")
    public SharedRouteResponse shareRoute(
            @Valid @RequestBody ShareRouteRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return routeMatchService.shareRoute(
                request,
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"));
    }

    @GetMapping("/shareable")
    @Operation(summary = "Lista las rutas compartidas activas del usuario autenticado")
    public List<SharedRouteResponse> getShareableRoutes(@AuthenticationPrincipal Jwt jwt) {
        return routeMatchService.getShareableRoutes(jwt.getSubject());
    }

    @DeleteMapping("/share/{routeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desactiva la publicación de una ruta compartida")
    public void unshareRoute(@PathVariable UUID routeId, @AuthenticationPrincipal Jwt jwt) {
        routeMatchService.unshareRoute(routeId, jwt.getSubject());
    }

    @GetMapping("/matches/suggestions")
    @Operation(summary = "Sugiere rutas compatibles de otros turistas (umbral >= 0.35)")
    public List<MatchSuggestionResponse> getSuggestions(
            @RequestParam(required = false) UUID routeId,
            @AuthenticationPrincipal Jwt jwt) {
        return routeMatchService.getSuggestions(routeId, jwt.getSubject());
    }

    @PostMapping("/matches/request")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Solicita un match con la ruta compartida de otro turista")
    public RouteMatchResponse requestMatch(
            @Valid @RequestBody MatchRequestDto request,
            @AuthenticationPrincipal Jwt jwt) {
        return routeMatchService.requestMatch(request, jwt.getSubject());
    }

    @GetMapping("/matches")
    @Operation(summary = "Lista los matches en los que participa el usuario autenticado")
    public List<RouteMatchResponse> listMatches(@AuthenticationPrincipal Jwt jwt) {
        return routeMatchService.listMatches(jwt.getSubject());
    }

    @GetMapping("/matches/{matchId}")
    @Operation(summary = "Obtiene el detalle de un match propio")
    public RouteMatchResponse getMatch(@PathVariable UUID matchId, @AuthenticationPrincipal Jwt jwt) {
        return routeMatchService.getMatch(matchId, jwt.getSubject());
    }

    @PostMapping("/matches/{matchId}/accept")
    @Operation(summary = "Acepta una solicitud de match pendiente (transición PENDING -> ACCEPTED)")
    public RouteMatchResponse acceptMatch(@PathVariable UUID matchId, @AuthenticationPrincipal Jwt jwt) {
        return routeMatchService.acceptMatch(matchId, jwt.getSubject());
    }

    @PostMapping("/matches/{matchId}/rate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Califica a la contraparte de un match aceptado (RN-14 a RN-16)")
    public MatchRatingResponse rateMatch(
            @PathVariable UUID matchId,
            @Valid @RequestBody RateMatchRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return matchRatingService.rate(matchId, parseUserId(jwt), request);
    }

    private static UUID parseUserId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new RutaturException(ErrorCode.VALIDATION_ERROR, "El subject del token no es un UUID válido");
        }
    }

    @PostMapping("/matches/{matchId}/reject")
    @Operation(summary = "Rechaza una solicitud de match pendiente (transición PENDING -> REJECTED)")
    public RouteMatchResponse rejectMatch(@PathVariable UUID matchId, @AuthenticationPrincipal Jwt jwt) {
        return routeMatchService.rejectMatch(matchId, jwt.getSubject());
    }

    @GetMapping("/matches/{matchId}/contact")
    @Operation(summary = "Revela los datos de contacto de la contraparte solo si el match está ACCEPTED")
    public MatchContactResponse getMatchContact(@PathVariable UUID matchId, @AuthenticationPrincipal Jwt jwt) {
        return routeMatchService.getMatchContact(matchId, jwt.getSubject());
    }
}
