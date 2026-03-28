package com.cosmos.fraud.ingestion.controller;

import com.cosmos.fraud.common.dto.ErrorResponse;
import com.cosmos.fraud.common.dto.ScoringResponse;
import com.cosmos.fraud.common.dto.TransactionRequest;
import com.cosmos.fraud.ingestion.service.TransactionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/transactions")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/score")
    public ResponseEntity<ScoringResponse> scoreTransaction(@Valid @RequestBody TransactionRequest request) {
        log.debug("Received scoring request for cardId={}", request.cardId());
        ScoringResponse response = transactionService.processTransaction(request);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .sorted()
                .collect(Collectors.joining("; "));

        log.warn("Validation failed: {}", message);

        ErrorResponse errorResponse = new ErrorResponse(
                "VALIDATION_ERROR",
                message,
                Instant.now(),
                null
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
}
