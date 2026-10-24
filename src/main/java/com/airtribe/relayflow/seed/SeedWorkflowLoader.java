package com.airtribe.relayflow.seed;

import com.airtribe.relayflow.catalog.NodeCatalog;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SeedWorkflowLoader implements ApplicationRunner {
	private static final String INSERT_SEED = """
        INSERT INTO workflows (id, name, description, status, definition)
        VALUES (?, ?, ?, 'published', ?::jsonb)
        ON CONFLICT (id) DO NOTHING
    """;

	private final ObjectMapper objectMapper;
	private final JdbcTemplate jdbcTemplate;
	private final NodeCatalog nodeCatalog;
	private final boolean enabled;

	public SeedWorkflowLoader(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
		NodeCatalog nodeCatalog, 
        @Value("${relay.seed.enabled:true}") boolean enabled) {

		this.objectMapper = objectMapper;
		this.jdbcTemplate = jdbcTemplate;
		this.nodeCatalog = nodeCatalog;
		this.enabled = enabled;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!enabled) {
			return;
		}

		JsonNode catalog = nodeCatalog.definition();
		if (catalog.path("nodes").isEmpty() || catalog.path("triggers").isEmpty()) {
			throw new IllegalStateException("Node catalog must contain node and trigger definitions");
		}

		JsonNode workflows = readSeedWorkflows();
		if (!workflows.isArray()) {
			throw new IllegalStateException("Seed workflow file must contain a workflows array");
		}

		Set<String> seenIds = new HashSet<>();
		for (JsonNode workflow : workflows) {
			String id = requiredText(workflow, "id");
			String name = requiredText(workflow, "name");
			if (!seenIds.add(id)) {
				throw new IllegalStateException("Duplicate seed workflow id: " + id);
			}
			String description = workflow.path("description").isMissingNode()
					? null
					: workflow.path("description").asString();
			jdbcTemplate.update(INSERT_SEED, id, name, description, workflow.toString());
		}
	}

	private JsonNode readSeedWorkflows() {
		try (var input = new ClassPathResource("data/seed_workflows.json").getInputStream()) {
			return objectMapper.readTree(input).path("workflows");
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to load data/seed_workflows.json", exception);
		}
	}

	private String requiredText(JsonNode node, String field) {
		String value = node.path(field).asString("").trim();
		if (value.isEmpty()) {
			throw new IllegalStateException("Seed workflow is missing required field: " + field);
		}
		return value;
	}
}