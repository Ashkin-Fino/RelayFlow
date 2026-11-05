package com.airtribe.relayflow.api.workflow.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.scheduling.support.CronExpression;

public final class CronSchedule {
	private CronSchedule() {
	}

	public static CronExpression parse(String expression) {
		if (expression == null || expression.isBlank()) {
			throw new IllegalArgumentException("Cron expression must not be blank");
		}
		String[] fields = expression.trim().split("\\s+");
		if (fields.length != 5) {
			throw new IllegalArgumentException("Cron expression must contain exactly five fields");
		}
		return CronExpression.parse("0 " + String.join(" ", fields));
	}

	public static Instant nextFireAt(String expression, Instant after) {
		ZonedDateTime next = parse(expression).next(after.atZone(ZoneOffset.UTC));
		if (next == null) {
			throw new IllegalArgumentException("Cron expression has no future execution time");
		}
		return next.toInstant();
	}
}
