package com.airtribe.relayflow.api.workflow.service;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class CronScheduleTest {
	@Test
	void calculatesNextFiveFieldCronExecutionInUtc() {
		Instant next = CronSchedule.nextFireAt("*/5 * * * *", Instant.parse("2026-11-04T10:02:30Z"));

		assertEquals(Instant.parse("2026-11-04T10:05:00Z"), next);
	}

	@Test
	void rejectsSixFieldExpression() {
		assertThrows(IllegalArgumentException.class, () -> CronSchedule.parse("0 */5 * * * *"));
	}
}
