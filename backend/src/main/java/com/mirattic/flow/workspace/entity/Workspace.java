package com.mirattic.flow.workspace.entity;

import com.mirattic.flow.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "workspaces")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workspace extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    private Workspace(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public static Workspace create(String name, String description) {
        return new Workspace(name, description);
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
