package org.group10.recommendation.exception;

import lombok.Getter;
import org.group10.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
public enum RecommendationErrorCode implements ErrorCode {
    INVALID_EVENT_TYPE(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    RecommendationErrorCode(HttpStatus status) {
        this.status = status;
    }
}
