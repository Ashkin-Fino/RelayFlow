package com.airtribe.relayflow.common.exception;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(WorkflowDefinitionException.class)
	public ResponseEntity<Map<String, Object>> invalidDefinition(WorkflowDefinitionException exception) {
		return error(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage(), exception.code());
	}

	@ExceptionHandler(WorkflowNotFoundException.class)
	public ResponseEntity<Map<String, Object>> notFound(WorkflowNotFoundException exception) {
		return error(HttpStatus.NOT_FOUND, exception.getMessage(), "workflow_not_found");
	}

	@ExceptionHandler(ApprovalNotFoundException.class)
	public ResponseEntity<Map<String, Object>> approvalNotFound(ApprovalNotFoundException exception) {
		return error(HttpStatus.NOT_FOUND, exception.getMessage(), "approval_not_found");
	}

	@ExceptionHandler(RunNotFoundException.class)
	public ResponseEntity<Map<String, Object>> runNotFound(RunNotFoundException exception) {
		return error(HttpStatus.NOT_FOUND, exception.getMessage(), "run_not_found");
	}

	@ExceptionHandler({ApprovalConflictException.class, RunConflictException.class})
	public ResponseEntity<Map<String, Object>> approvalOrRunConflict(RuntimeException exception) {
		return error(HttpStatus.CONFLICT, exception.getMessage(), "state_conflict");
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> invalidArgument(IllegalArgumentException exception) {
		return error(HttpStatus.BAD_REQUEST, exception.getMessage(), "invalid_request");
	}

	@ExceptionHandler(WorkflowConflictException.class)
	public ResponseEntity<Map<String, Object>> conflict(WorkflowConflictException exception) {
		return error(HttpStatus.CONFLICT, exception.getMessage(), "workflow_already_exists");
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> unreadableBody() {
		return error(HttpStatus.BAD_REQUEST, "Request body must contain valid JSON", "invalid_json");
	}

	@ExceptionHandler(DataAccessException.class)
	public ResponseEntity<Map<String, Object>> databaseError(DataAccessException exception) {
		log.error("Database operation failed while handling an API request", exception);
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "Database operation failed", "database_error");
	}

	@ExceptionHandler(InvalidTriggerInputException.class)
	public ResponseEntity<Map<String, Object>> invalidTriggerInput(InvalidTriggerInputException exception) {
		log.error("Invalid trigger input", exception);
		return error(HttpStatus.BAD_REQUEST, exception.getMessage(), "invalid_trigger_input");
	}

	@ExceptionHandler(InvalidTriggerTypeException.class)
	public ResponseEntity<Map<String, Object>> invalidTriggerType(InvalidTriggerTypeException exception) {
		log.error("Invalid trigger type", exception);
		return error(HttpStatus.CONFLICT, exception.getMessage(), "invalid_trigger_type");
	}

	@ExceptionHandler(WorkflowNotPublishedException.class)
	public ResponseEntity<Map<String, Object>> workflowNotPublished(WorkflowNotPublishedException exception) {
		log.error("Workflow not published", exception);
		return error(HttpStatus.CONFLICT, exception.getMessage(), "workflow_not_published");
	}

	@ExceptionHandler(WebhookUnauthorizedException.class)
	public ResponseEntity<Map<String, Object>> unauthorizedWebhook(WebhookUnauthorizedException exception) {
		log.error("Unauthorized webhook access", exception);
		return error(HttpStatus.UNAUTHORIZED, exception.getMessage(), "invalid_webhook_secret");
	}

	private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message, String code) {
		return ResponseEntity.status(status).body(Map.of("error", Map.of("message", message, "code", code)));
	}
}