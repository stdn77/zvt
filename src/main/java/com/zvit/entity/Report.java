package com.zvit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 20)
    private ReportType reportType;

    @Column(name = "simple_response", length = 50)
    private String simpleResponse;

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "field1_value", length = 200)
    private String field1Value;

    @Column(name = "field2_value", length = 200)
    private String field2Value;

    @Column(name = "field3_value", length = 200)
    private String field3Value;

    @Column(name = "field4_value", length = 200)
    private String field4Value;

    @Column(name = "field5_value", length = 200)
    private String field5Value;

    @Column(name = "is_urgent")
    @lombok.Builder.Default
    private Boolean isUrgent = false;

    @Column(name = "deadline_at")
    private LocalDateTime deadlineAt;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    public enum ReportType {
        SIMPLE,
        EXTENDED,
        URGENT
    }

    @PrePersist
    protected void onCreate() {
        submittedAt = LocalDateTime.now();
    }
}