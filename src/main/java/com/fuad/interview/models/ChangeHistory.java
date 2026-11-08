package com.fuad.interview.models;

import jakarta.annotation.Nonnull;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "CHANGE_HISTORY")
@Getter
@Setter
public class ChangeHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CHANGE_HISTORY_ID")
    private int chageHistoryId;

    @Nonnull
    @Column(name = "CHANGE_DATE", nullable = false)
    private LocalDateTime changeDate;

    @Nonnull
    @Column(name = "CHANGE_DESCRIPTION", nullable = false, length = 500)
    private String changeDescription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false)
    private Users reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "EVENT_ID", nullable = false)
    private Event event;
}
