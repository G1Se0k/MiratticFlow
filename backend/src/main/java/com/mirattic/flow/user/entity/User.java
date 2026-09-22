package com.mirattic.flow.user.entity;

import com.mirattic.flow.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 전용. 외부에서는 create() 로만 만든다.
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    /** BCrypt 해시. 평문은 어디에도 저장하지 않는다. */
    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    private User(String email, String password, String name) {
        this.email = email;
        this.password = password;
        this.name = name;
    }

    public static User create(String email, String encodedPassword, String name) {
        return new User(email, encodedPassword, name);
    }
}
