package com.napcat.core.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RoleFilter {
    Role value();

    enum Role {
        OWNER, ADMIN, MEMBER, SUPERUSER
    }
}
