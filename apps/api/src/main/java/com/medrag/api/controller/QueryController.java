package com.medrag.api.controller;

import com.medrag.api.ai.AiClient;
import com.medrag.api.query.QueryService;
import com.medrag.api.security.CurrentPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/queries")
public class QueryController {
    private final QueryService service;

    public QueryController(QueryService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE')")
    public AiClient.QueryResponse query(@Valid @RequestBody Request request) {
        return service.query(
                CurrentPrincipal.require(),
                request.question(),
                request.documentIds(),
                request.topK()
        );
    }

    public record Request(
            @NotBlank @Size(min = 3, max = 2000) String question,
            @NotEmpty @Size(max = 20) List<UUID> documentIds,
            @Min(1) @Max(20) int topK
    ) {
        public Request {
            if (topK == 0) {
                topK = 8;
            }
        }
    }
}
