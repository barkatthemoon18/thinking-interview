package com.fuad.interview.models;

import jakarta.annotation.Nonnull;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "CORRECTIVE_ACTION")
@Getter
@Setter
public class CorrectiveAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ACTION_ID")
    private int actionId;

    @Nonnull
    @Column(name = "DESCRIPTION", nullable = false, length = 500)
    private String description;

    @Nonnull
    @Column(name = "RESPONSIBLE", nullable = false, length = 30)
    private String responsible;

    @Nonnull
    @Column(name = "TARGET_DATE", nullable = false)
    private LocalDateTime targetDate;

    @Nonnull
    @Column(name = "STATUS", nullable = false, length = 30)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "EVENT_ID", nullable = false)
    private Event event;
}
