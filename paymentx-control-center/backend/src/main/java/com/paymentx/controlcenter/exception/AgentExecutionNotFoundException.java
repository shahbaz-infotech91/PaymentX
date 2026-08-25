package com.paymentx.controlcenter.exception;

/**
 * Thrown by AgentExecutionService.executionDetail(...) when no audit_event row matches the
 * requested executionId. A dedicated type (rather than reusing generic ControlCenterException,
 * which GlobalExceptionHandler always maps to 502 Bad Gateway - the correct status for a real
 * upstream-call failure) so a simple "this record does not exist" lookup miss maps to the
 * semantically correct 404 Not Found, mirroring AiServiceNotReadyException's own precedent for
 * why a distinct outcome needs a distinct exception type/handler rather than the generic one.
 */
public class AgentExecutionNotFoundException extends ControlCenterException {

    public AgentExecutionNotFoundException(String executionId) {
        super("EXECUTION_NOT_FOUND", "No agent execution found with executionId: " + executionId);
    }
}
