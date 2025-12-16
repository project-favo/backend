package com.favo.backend.Domain.Common;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity

public class DbTest {
    @Id
    @GeneratedValue
    private Long id;
}

