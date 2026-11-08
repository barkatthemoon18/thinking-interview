package com.fuad.interview.dto;

import java.time.LocalDateTime;

public record ChangeHistoryDto(int changeHistoryId, LocalDateTime changeDate, String actor, String description) {
}
