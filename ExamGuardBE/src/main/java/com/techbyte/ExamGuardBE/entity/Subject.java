package com.techbyte.ExamGuardBE.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "subjects", uniqueConstraints = {@UniqueConstraint(columnNames = "code")})
public class Subject extends BaseEntity {

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", nullable = true, columnDefinition = "TEXT")
    private String description;

}
