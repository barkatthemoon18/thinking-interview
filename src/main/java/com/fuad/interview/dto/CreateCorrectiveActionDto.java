package com.fuad.interview.dto;

import java.time.LocalDateTime;

public record CreateCorrectiveActionDto(String description, String responsible, LocalDateTime targetDate) {
}
