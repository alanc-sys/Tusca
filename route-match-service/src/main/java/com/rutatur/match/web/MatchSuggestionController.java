package com.rutatur.match.web;

import com.rutatur.common.dto.MatchSuggestionDto;
import com.rutatur.match.similarity.SuggestionService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Similarity suggestions; never exposes contact data or user identifiers. */
@RestController
@RequestMapping("/api/routes/matches")
@PreAuthorize("hasRole('USER')")
public class MatchSuggestionController {

    private final SuggestionService suggestionService;

    public MatchSuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @GetMapping("/suggestions")
    public List<MatchSuggestionDto> suggestions(@AuthenticationPrincipal Jwt jwt) {
        return suggestionService.suggestions(jwt.getSubject());
    }
}
