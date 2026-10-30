package com.airtribe.relayflow.workflow.exceptions;

import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class WorkflowApiExceptionHandler {
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

	private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message, String code) {
		return ResponseEntity.status(status).body(Map.of("error", Map.of("message", message, "code", code)));
	}
}