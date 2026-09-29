package com.join.back.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An error an API method can answer with; added to the spec by {@link OpenApiConfig}, 4xx with the
 * ErrorResponse body. Used instead of springdoc's @ApiResponse, which would drop the 2xx response.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ApiError.List.class)
public @interface ApiError {

    String code();

    String description();

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        ApiError[] value();
    }
}
