package com.rutatur.match.state;

import com.rutatur.common.enums.MatchStatus;
import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;
import com.rutatur.match.entity.RouteMatchEntity;

public class RejectedMatchState implements MatchState {

    @Override
    public MatchStatus getStatus() {
        return MatchStatus.REJECTED;
    }

    @Override
    public void accept(RouteMatchEntity match, String actorUserId) {
        throw new RutaturException(
                ErrorCode.INVALID_STATE_TRANSITION,
                "El match fue rechazado (REJECTED) y no puede aceptarse");
    }

    @Override
    public void reject(RouteMatchEntity match, String actorUserId) {
        throw new RutaturException(
                ErrorCode.INVALID_STATE_TRANSITION,
                "El match ya se encuentra en estado REJECTED y no puede modificarse");
    }

    @Override
    public boolean canRevealContact() {
        return false;
    }
}
