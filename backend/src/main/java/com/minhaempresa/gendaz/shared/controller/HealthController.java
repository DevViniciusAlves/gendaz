package com.minhaempresa.gendaz.shared.controller;

import com.minhaempresa.gendaz.shared.dto.HealthDtos;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping({"/health", "/api/health"})
    public ResponseEntity<HealthDtos.HealthResponse> health() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new HealthDtos.HealthResponse("ok"));
    }
}

