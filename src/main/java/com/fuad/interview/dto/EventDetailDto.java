package com.fuad.interview.dto;

import java.time.LocalDate;
import java.util.List;

public record EventDetailDto(int eventId, String title, String description, LocalDate date, String area,
                             String status, String reporter, List<CorrectiveActionDto> actions,
                             List<ChangeHistoryDto> history) {
}
