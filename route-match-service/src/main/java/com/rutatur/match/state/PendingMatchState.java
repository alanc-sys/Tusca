package com.rutatur.match.state;

import com.rutatur.common.enums.MatchStatus;
import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;
import com.rutatur.match.entity.RouteMatchEntity;

import java.time.Instant;

public class PendingMatchState implements MatchState {

    @Override
    public MatchStatus getStatus() {
        return MatchStatus.PENDING;
    }

    @Override
    public void accept(RouteMatchEntity match, String actorUserId) {
        validateRecipient(match, actorUserId, "El solicitante no puede auto-aceptar su propia solicitud");
        match.setConsentB(true);
        match.setStatus(MatchStatus.ACCEPTED);
        match.setUpdatedAt(Instant.now());
    }

    @Override
    public void reject(RouteMatchEntity match, String actorUserId) {
        validateRecipient(match, actorUserId, "Solo el destinatario puede rechazar la solicitud");
        match.setStatus(MatchStatus.REJECTED);
        match.setUpdatedAt(Instant.now());
    }

    @Override
    public boolean canRevealContact() {
        return false;
    }

    private void validateRecipient(RouteMatchEntity match, String actorUserId, String sameRequesterMessage) {
        if (!match.involvesUser(actorUserId)) {
            throw new RutaturException(ErrorCode.FORBIDDEN, "No tenés permiso para operar sobre este match");
        }
        if (match.getSharedRouteA().getUserId().equals(actorUserId)) {
            throw new RutaturException(ErrorCode.BUSINESS_RULE_VIOLATION, sameRequesterMessage);
        }
    }
}
