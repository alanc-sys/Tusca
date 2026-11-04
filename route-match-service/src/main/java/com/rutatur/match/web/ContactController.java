package com.rutatur.match.web;

import com.rutatur.common.dto.ContactDto;
import com.rutatur.match.service.ContactRevealService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Minimal contact reveal, gated on mutual acceptance. */
@RestController
@RequestMapping("/api/routes/matches")
@PreAuthorize("hasRole('USER')")
public class ContactController {

    private final ContactRevealService contactRevealService;

    public ContactController(ContactRevealService contactRevealService) {
        this.contactRevealService = contactRevealService;
    }

    @GetMapping("/{matchId}/contact")
    public ContactDto contact(@PathVariable UUID matchId, @AuthenticationPrincipal Jwt jwt) {
        return contactRevealService.reveal(matchId, jwt.getSubject());
    }
}
