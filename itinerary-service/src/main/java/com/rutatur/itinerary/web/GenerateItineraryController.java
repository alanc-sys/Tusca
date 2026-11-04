package com.rutatur.itinerary.web;

import com.rutatur.common.dto.ItineraryRequestDto;
import com.rutatur.common.dto.ItineraryResponseDto;
import com.rutatur.itinerary.service.GenerationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public generation endpoint. */
@RestController
@RequestMapping("/api/itinerary")
public class GenerateItineraryController {

    private final GenerationService generationService;

    public GenerateItineraryController(GenerationService generationService) {
        this.generationService = generationService;
    }

    @PostMapping("/generate")
    public ItineraryResponseDto generate(@Valid @RequestBody ItineraryRequestDto request) {
        return generationService.generate(request);
    }
}
