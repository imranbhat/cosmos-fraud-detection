package com.cosmos.fraud.threeds.service;

import com.cosmos.fraud.threeds.dto.ChallengeResultRequest;
import com.cosmos.fraud.threeds.dto.ChallengeResultResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages 3DS challenge sessions and processes challenge completion callbacks.
 *
 * <p>Challenge sessions are stored in-memory using a {@link ConcurrentHashMap}.
 * In a production deployment this should be backed by a distributed cache
 * (e.g. Redis) to support horizontal scaling.
 *
 * <p>ECI (Electronic Commerce Indicator) assignment is simplified:
 * <ul>
 *   <li>Visa cards    → 05 (fully authenticated)</li>
 *   <li>Mastercard    → 02 (fully authenticated)</li>
 *   <li>Unknown / default → 07</li>
 * </ul>
 */
@Service
public class ChallengeService {

    private static final Logger log = LoggerFactory.getLogger(ChallengeService.class);

    static final String TRANS_STATUS_SUCCESS = "Y";
    static final String TRANS_STATUS_FAILED  = "N";

    private static final String ECI_VISA_AUTHENTICATED  = "05";
    private static final String ECI_MC_AUTHENTICATED    = "02";
    private static final String ECI_UNKNOWN             = "07";

    /**
     * In-memory session store: transID → {@link ChallengeSession}.
     */
    private final ConcurrentHashMap<String, ChallengeSession> sessions = new ConcurrentHashMap<>();

    /**
     * Registers a new challenge session before redirecting the cardholder to the ACS.
     *
     * @param threeDSServerTransID server transaction ID
     * @param riskScore            risk score that triggered the challenge
     */
    public void registerChallenge(String threeDSServerTransID, int riskScore) {
        ChallengeSession session = new ChallengeSession(threeDSServerTransID, riskScore, SessionState.PENDING);
        sessions.put(threeDSServerTransID, session);
        log.debug("Challenge session registered: transID={} riskScore={}", threeDSServerTransID, riskScore);
    }

    /**
     * Processes the challenge completion callback from the ACS.
     *
     * @param request the challenge result submitted by the ACS / cardholder
     * @return {@link ChallengeResultResponse} with final authentication outcome
     * @throws IllegalArgumentException if the session is not found
     */
    public ChallengeResultResponse processChallenge(ChallengeResultRequest request) {
        String transID = request.threeDSServerTransID();

        ChallengeSession session = sessions.get(transID);
        if (session == null) {
            log.warn("No challenge session found for transID={}", transID);
            throw new IllegalArgumentException("Challenge session not found for transID: " + transID);
        }

        boolean authenticated = TRANS_STATUS_SUCCESS.equalsIgnoreCase(request.transStatus());
        String eci            = resolveEci(session, authenticated);
        String finalStatus    = authenticated ? TRANS_STATUS_SUCCESS : TRANS_STATUS_FAILED;

        // Update session state
        sessions.put(transID, session.withState(authenticated ? SessionState.COMPLETED : SessionState.FAILED));

        log.info("Challenge processed: transID={} status={} eci={}", transID, finalStatus, eci);

        return new ChallengeResultResponse(
                transID,
                finalStatus,
                request.authenticationValue(),
                eci
        );
    }

    /**
     * Returns the number of active (non-terminal) challenge sessions — useful for health checks.
     */
    public int activeSessions() {
        return (int) sessions.values().stream()
                .filter(s -> s.state() == SessionState.PENDING)
                .count();
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private String resolveEci(ChallengeSession session, boolean authenticated) {
        if (!authenticated) {
            return ECI_UNKNOWN;
        }
        // Simplified card-scheme detection based on riskScore parity (placeholder logic).
        // In production, the card BIN / scheme is passed as part of the request.
        return (session.riskScore() % 2 == 0) ? ECI_VISA_AUTHENTICATED : ECI_MC_AUTHENTICATED;
    }

    // ---------------------------------------------------------------------------
    // Internal types
    // ---------------------------------------------------------------------------

    public enum SessionState {
        PENDING,
        COMPLETED,
        FAILED
    }

    public record ChallengeSession(
            String threeDSServerTransID,
            int riskScore,
            SessionState state
    ) {
        public ChallengeSession withState(SessionState newState) {
            return new ChallengeSession(threeDSServerTransID, riskScore, newState);
        }
    }
}
