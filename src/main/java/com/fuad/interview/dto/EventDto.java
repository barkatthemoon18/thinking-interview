package com.fuad.interview.dto;

import java.time.LocalDate;

public record EventDto(String title, String description, LocalDate date, String area) {
}
