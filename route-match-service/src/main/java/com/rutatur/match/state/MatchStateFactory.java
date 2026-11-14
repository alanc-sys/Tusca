package com.rutatur.match.state;

import com.rutatur.common.enums.MatchStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class MatchStateFactory {

    private final Map<MatchStatus, MatchState> states = new EnumMap<>(MatchStatus.class);

    public MatchStateFactory() {
        states.put(MatchStatus.PENDING, new PendingMatchState());
        states.put(MatchStatus.ACCEPTED, new AcceptedMatchState());
        states.put(MatchStatus.REJECTED, new RejectedMatchState());
    }

    public MatchState of(MatchStatus status) {
        MatchState state = states.get(status);
        if (state == null) {
            throw new IllegalArgumentException("Estado de match no soportado: " + status);
        }
        return state;
    }
}
