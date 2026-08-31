package com.minhaempresa.gendaz.shared.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SentryTestController {

    @GetMapping("/api/test/sentry-error")
    public void testSentryError() {
        throw new RuntimeException("Sentry test - stage");
    }
}