package org.eclipse.openvsx.db.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.persistence.AttributeConverter;

public class EclipseDataConverter implements AttributeConverter<EclipseData, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public EclipseDataConverter() {
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    @Override
    public String convertToDatabaseColumn(EclipseData data) {
        if (data == null)
            return null;
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException exc) {
            throw new RuntimeException("Failed to serialize EclipseData to DB column.", exc);
        }
    }

    @Override
    public EclipseData convertToEntityAttribute(String raw) {
        if (raw == null)
            return null;
        try {
            return objectMapper.readValue(raw, EclipseData.class);
        } catch (JsonProcessingException exc) {
            throw new RuntimeException("Failed to parse EclipseData from DB column.", exc);
        }
    }

}
