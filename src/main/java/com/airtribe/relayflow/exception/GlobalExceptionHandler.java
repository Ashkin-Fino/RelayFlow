package com.airtribe.relayflow.exception;

import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.airtribe.relayflow.run.exceptions.InvalidTriggerInputException;
import com.airtribe.relayflow.run.exceptions.InvalidTriggerTypeException;
import com.airtribe.relayflow.run.exceptions.WebhookUnauthorizedException;
import com.airtribe.relayflow.run.exceptions.WorkflowNotPublishedException;
import com.airtribe.relayflow.workflow.exceptions.WorkflowConflictException;
import com.airtribe.relayflow.workflow.exceptions.WorkflowDefinitionException;
import com.airtribe.relayflow.workflow.exceptions.WorkflowNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
	@ExceptionHandler(WorkflowDefinitionException.class)
	public ResponseEntity<Map<String, Object>> invalidDefinition(WorkflowDefinitionException exception) {
		return error(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage(), exception.code());
	}

	@ExceptionHandler(WorkflowNotFoundException.class)
	public ResponseEntity<Map<String, Object>> notFound(WorkflowNotFoundException exception) {
		return error(HttpStatus.NOT_FOUND, exception.getMessage(), "workflow_not_found");
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
	public ResponseEntity<Map<String, Object>> databaseError() {
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "Database operation failed", "database_error");
	}

	@ExceptionHandler(InvalidTriggerInputException.class)
	public ResponseEntity<Map<String, Object>> invalidTriggerInput(InvalidTriggerInputException exception) {
		return error(HttpStatus.BAD_REQUEST, exception.getMessage(), "invalid_trigger_input");
	}

	@ExceptionHandler(InvalidTriggerTypeException.class)
	public ResponseEntity<Map<String, Object>> invalidTriggerType(InvalidTriggerTypeException exception) {
		return error(HttpStatus.CONFLICT, exception.getMessage(), "invalid_trigger_type");
	}

	@ExceptionHandler(WorkflowNotPublishedException.class)
	public ResponseEntity<Map<String, Object>> workflowNotPublished(WorkflowNotPublishedException exception) {
		return error(HttpStatus.CONFLICT, exception.getMessage(), "workflow_not_published");
	}

	@ExceptionHandler(WebhookUnauthorizedException.class)
	public ResponseEntity<Map<String, Object>> unauthorizedWebhook(WebhookUnauthorizedException exception) {
		return error(HttpStatus.UNAUTHORIZED, exception.getMessage(), "invalid_webhook_secret");
	}

	private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message, String code) {
		return ResponseEntity.status(status).body(Map.of("error", Map.of("message", message, "code", code)));
	}
}