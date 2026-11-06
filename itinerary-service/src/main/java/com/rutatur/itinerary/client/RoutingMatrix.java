package com.rutatur.itinerary.client;

/**
 * Travel-time matrix from the routing engine. Durations are minutes, distances meters.
 * {@link #UNREACHABLE} marks a leg with no route.
 */
public record RoutingMatrix(int[][] durationsMinutes, int[][] distancesMeters) {

    public static final int UNREACHABLE = Integer.MAX_VALUE;

    public int durationMinutes(int from, int to) {
        return cell(durationsMinutes, from, to);
    }

    public int distanceMeters(int from, int to) {
        return cell(distancesMeters, from, to);
    }

    private static int cell(int[][] values, int from, int to) {
        if (values == null || from < 0 || to < 0 || from >= values.length) {
            return UNREACHABLE;
        }
        int[] row = values[from];
        if (row == null || to >= row.length) {
            return UNREACHABLE;
        }
        return row[to];
    }
}
