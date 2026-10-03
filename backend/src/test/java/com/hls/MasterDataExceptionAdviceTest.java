package com.hls;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.school.api.ConflictException;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class MasterDataExceptionAdviceTest {

    private final MasterDataExceptionAdvice advice = new MasterDataExceptionAdvice();

    @Test
    void mapsEachExceptionToItsContractStatusAndBody() {
        var notFound = advice.notFound(new NotFoundException("No such record."));
        assertThat(notFound.getStatusCode().value()).isEqualTo(404);
        assertThat(notFound.getBody().reason()).isEqualTo("No such record.");

        assertThat(advice.conflict(new ConflictException("x")).getStatusCode().value())
                .isEqualTo(409);
        assertThat(advice.invalid(new InvalidInputException("x")).getStatusCode().value())
                .isEqualTo(400);
        assertThat(advice.forbidden(new ForbiddenFieldException("x")).getStatusCode().value())
                .isEqualTo(403);
    }

    @Test
    void optimisticLockFailureIsA409WithTheStaleMessage() {
        var resp = advice.stale(new ObjectOptimisticLockingFailureException(Object.class, "id"));

        assertThat(resp.getStatusCode().value()).isEqualTo(409);
        assertThat(resp.getBody().reason()).isEqualTo("This record was changed by someone else. Reload and try again.");
    }
}
