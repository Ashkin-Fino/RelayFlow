package com.airtribe.relayflow.api.approval;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.airtribe.relayflow.api.approval.dto.ApprovalDecisionResponse;
import com.airtribe.relayflow.api.approval.dto.ApprovalSummary;
import com.airtribe.relayflow.api.approval.service.ApprovalService;

import tools.jackson.databind.JsonNode;

@RestController
public class ApprovalController {
	private final ApprovalService service;

	public ApprovalController(ApprovalService service) {
		this.service = service;
	}

	@GetMapping("/approvals")
	public List<ApprovalSummary> list(@RequestParam(defaultValue = "pending") String status) {
		return service.list(status);
	}

	@PostMapping("/approvals/{approvalId}/approve")
	public ResponseEntity<ApprovalDecisionResponse> approve(@PathVariable String approvalId) {
		return ResponseEntity.ok(service.approve(approvalId));
	}

	@PostMapping("/approvals/{approvalId}/reject")
	public ResponseEntity<ApprovalDecisionResponse> reject(
			@PathVariable String approvalId, @RequestBody(required = false) JsonNode request) {
		String reason = request == null ? null : request.path("reason").asString(null);
		return ResponseEntity.ok(service.reject(approvalId, reason));
	}
}
