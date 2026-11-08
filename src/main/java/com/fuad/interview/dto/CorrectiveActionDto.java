package com.fuad.interview.dto;

import java.time.LocalDateTime;

public record CorrectiveActionDto(int actionId, String description, String responsible,
                                  LocalDateTime targetDate, String status) {
}
