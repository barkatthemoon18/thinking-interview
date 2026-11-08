package com.fuad.interview.models;

import jakarta.annotation.Nonnull;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "EVENT")
@Getter
@Setter
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EVENT_ID")
    private int eventId;

    @Nonnull
    @Column(name = "TITLE", nullable = false, length = 120)
    private String title;

    @Nonnull
    @Column(name = "DESCRIPTION", nullable = false, length = 2000)
    private String description;

    @Nonnull
    @Column(name = "EVENT_DATE", nullable = false)
    private LocalDate eventDate;

    @Nonnull
    @Column(name = "EVENT_AREA", nullable = false, length = 125)
    private String eventArea;

    @Nonnull
    @Column(name = "STATUS", nullable = false, length = 30)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "REPORTER_ID", nullable = false)
    private Users reporter;

    @OneToMany(mappedBy = "event")
    private List<CorrectiveAction> correctiveActions = new ArrayList<>();

    @OneToMany(mappedBy = "event")
    private List<ChangeHistory> changeHistories = new ArrayList<>();
}
