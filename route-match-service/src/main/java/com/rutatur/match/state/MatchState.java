package com.rutatur.match.state;

import com.rutatur.common.enums.MatchStatus;
import com.rutatur.match.entity.RouteMatchEntity;

/**
 * Contrato del patrón State para el ciclo de vida de un match (RN-06, RN-08, RN-09).
 */
public interface MatchState {

    MatchStatus getStatus();

    void accept(RouteMatchEntity match, String actorUserId);

    void reject(RouteMatchEntity match, String actorUserId);

    boolean canRevealContact();
}
