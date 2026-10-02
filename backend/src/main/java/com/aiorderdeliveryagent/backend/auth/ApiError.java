package com.aiorderdeliveryagent.backend.auth;

import java.time.Instant;
import java.util.Map;

record ApiError(String code, String message, Map<String, String> fieldErrors, Instant timestamp) {
}
