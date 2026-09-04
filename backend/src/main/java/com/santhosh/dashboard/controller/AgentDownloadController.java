package com.santhosh.dashboard.controller;

import java.io.IOException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/downloads")
public class AgentDownloadController {

    private static final String AGENT_FILE =
            "agent/sentinelcore-agent-1.0.0.jar";

    @GetMapping("/windows-agent")
    public ResponseEntity<Resource> downloadWindowsAgent()
            throws IOException {

        ClassPathResource resource =
                new ClassPathResource(AGENT_FILE);

        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(
                        MediaType.APPLICATION_OCTET_STREAM
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"sentinelcore-agent-1.0.0.jar\""
                )
                .contentLength(resource.contentLength())
                .body(resource);
    }
}