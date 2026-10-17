package com.lexor.matching.entity;

public enum Role {
    RIDER,
    DRIVER,
    ADMIN;

    public String toAuthority() {
        return "ROLE_" + this.name();
    }
}
