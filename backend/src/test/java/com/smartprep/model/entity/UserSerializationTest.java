package com.smartprep.model.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class UserSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void passwordHashIsNotSerializedOrIncludedInToString() throws Exception {
        String testHash = "not-a-real-password-hash";
        User user = User.builder()
                .userId(1L)
                .username("test-user")
                .passwordHash(testHash)
                .build();

        String json = objectMapper.writeValueAsString(user);

        assertFalse(json.contains("passwordHash"));
        assertFalse(json.contains(testHash));
        assertFalse(user.toString().contains(testHash));
    }
}
