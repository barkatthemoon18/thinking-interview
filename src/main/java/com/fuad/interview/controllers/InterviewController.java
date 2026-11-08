package com.fuad.interview.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class InterviewController {
    private static final Logger LOGGER = LoggerFactory.getLogger(InterviewController.class);

    @GetMapping("/events")
    public ResponseEntity<String> getEvents() {
        return null;
    }

    @PostMapping("/create-event")
    public ResponseEntity<String> createEvent() {
        return null;
    }
}
