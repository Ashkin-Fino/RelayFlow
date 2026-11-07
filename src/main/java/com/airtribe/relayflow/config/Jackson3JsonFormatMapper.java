package com.airtribe.relayflow.config;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;

import tools.jackson.databind.ObjectMapper;

public class Jackson3JsonFormatMapper implements FormatMapper {
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public <T> T fromString(CharSequence json, JavaType<T> javaType, WrapperOptions options) {
		return javaType.wrap(objectMapper.readTree(json.toString()), options);
	}

	@Override
	public <T> String toString(T value, JavaType<T> javaType, WrapperOptions options) {
		return objectMapper.writeValueAsString(value);
	}
}
