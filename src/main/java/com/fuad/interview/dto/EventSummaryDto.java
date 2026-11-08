package com.fuad.interview.dto;

import java.time.LocalDate;

public record EventSummaryDto(int eventId, String title, LocalDate date, String area, String status, String reporter) {
}
