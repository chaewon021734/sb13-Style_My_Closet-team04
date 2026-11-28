package com.project.sb13ootbsteam04.user.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_operation_locks")
public class AdminOperationLock {

    public static final Integer LOCK_ID = 1;

    @Id
    private Integer id;

    protected AdminOperationLock() {
        // JPA 기본 생성자
    }

    public AdminOperationLock(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }
}
