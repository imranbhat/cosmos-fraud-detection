package com.cosmos.fraud.threeds.controller;

import com.cosmos.fraud.threeds.dto.ChallengeResultRequest;
import com.cosmos.fraud.threeds.dto.ChallengeResultResponse;
import com.cosmos.fraud.threeds.dto.RiskAssessmentRequest;
import com.cosmos.fraud.threeds.dto.RiskAssessmentResponse;
import com.cosmos.fraud.threeds.service.ChallengeService;
import com.cosmos.fraud.threeds.service.RiskBasedAuthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * REST controller exposing the 3DS authentication endpoints.
 *
 * <ul>
 *   <li>{@code POST /v1/threeds/risk-assessment} – initiate a risk-based authentication check</li>
 *   <li>{@code POST /v1/threeds/challenge-result} – submit the ACS challenge outcome</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/threeds")
public class ThreeDSController {

    private static final Logger log = LoggerFactory.getLogger(ThreeDSController.class);

    private final RiskBasedAuthService riskBasedAuthService;
    private final ChallengeService     challengeService;

    public ThreeDSController(RiskBasedAuthService riskBasedAuthService,
                             ChallengeService challengeService) {
        this.riskBasedAuthService = riskBasedAuthService;
        this.challengeService     = challengeService;
    }

    /**
     * Assesses the fraud risk and returns a 3DS authentication decision.
     *
     * @param request validated risk assessment request
     * @return 200 OK with {@link RiskAssessmentResponse}
     */
    @PostMapping("/risk-assessment")
    public ResponseEntity<RiskAssessmentResponse> riskAssessment(
            @Valid @RequestBody RiskAssessmentRequest request) {

        log.debug("Risk assessment request: cardId={} merchantId={} amount={} {}",
                request.cardId(), request.merchantId(), request.amount(), request.currency());

        RiskAssessmentResponse response = riskBasedAuthService.assessRisk(request);

        // Auto-register challenge session when ACS challenge is mandated
        if (response.acsChallengeMandated()) {
            challengeService.registerChallenge(response.threeDSServerTransID(), response.riskScore());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Processes the 3DS challenge result returned by the ACS.
     *
     * @param request validated challenge result request
     * @return 200 OK with {@link ChallengeResultResponse}
     */
    @PostMapping("/challenge-result")
    public ResponseEntity<ChallengeResultResponse> challengeResult(
            @Valid @RequestBody ChallengeResultRequest request) {

        log.debug("Challenge result: transID={} status={}",
                request.threeDSServerTransID(), request.transStatus());

        ChallengeResultResponse response = challengeService.processChallenge(request);
        return ResponseEntity.ok(response);
    }

    // ---------------------------------------------------------------------------
    // Exception handlers
    // ---------------------------------------------------------------------------

    /**
     * Handles Bean Validation failures with RFC 9457 Problem Details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setType(URI.create("https://cosmos.fraud/problems/validation-error"));
        problem.setTitle("Validation Error");
        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Handles unknown challenge session lookups.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create("https://cosmos.fraud/problems/session-not-found"));
        problem.setTitle("Challenge Session Not Found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }
}
