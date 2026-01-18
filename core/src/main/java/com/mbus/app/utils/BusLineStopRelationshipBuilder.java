package com.mbus.app.utils;

import com.mbus.app.model.BusLine;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.Geolocation;

import java.util.*;

public class BusLineStopRelationshipBuilder {

    public static RelationshipResult buildRelationships(
        List<BusLine> originalLines,
        List<BusStop> originalStops,
        double proximityThreshold) {

        Map<Integer, Set<Integer>> stopToLinesMap = new HashMap<Integer, Set<Integer>>();
        Map<String, List<BusStop>> lineToStopsMap = new HashMap<String, List<BusStop>>();

        for (BusStop stop : originalStops) {
            stopToLinesMap.put(stop.idAvpost, new TreeSet<Integer>());
        }

        for (BusLine line : originalLines) {
            String lineKey = getLineKey(line);
            lineToStopsMap.put(lineKey, new ArrayList<BusStop>());
        }

        for (BusLine line : originalLines) {
            String lineKey = getLineKey(line);
            List<BusStop> stopsForLine = new ArrayList<BusStop>();

            for (BusStop stop : originalStops) {
                if (isStopOnLine(stop, line, proximityThreshold)) {
                    stopsForLine.add(stop);
                    stopToLinesMap.get(stop.idAvpost).add(line.lineId);
                }
            }

            stopsForLine = sortStopsByLineOrder(stopsForLine, line);
            lineToStopsMap.put(lineKey, stopsForLine);
        }

        List<BusLine> updatedLines = new ArrayList<BusLine>();
        for (BusLine line : originalLines) {
            String lineKey = getLineKey(line);
            List<BusStop> stops = lineToStopsMap.get(lineKey);

            BusLine updatedLine = new BusLine(
                line.lineId,
                line.variantId,
                line.direction,
                line.length,
                line.name,
                line.note,
                line.providerName,
                line.providerLink,
                line.getPath(),
                line.getOriginalCoordinates(),
                stops
            );
            updatedLines.add(updatedLine);
        }

        List<BusStop> updatedStops = new ArrayList<BusStop>();
        for (BusStop stop : originalStops) {
            Set<Integer> lineIds = stopToLinesMap.get(stop.idAvpost);
            List<Integer> sortedLineIds = new ArrayList<Integer>(lineIds);
            Collections.sort(sortedLineIds);

            BusStop updatedStop = new BusStop(
                stop.idAvpost,
                stop.idMarprom,
                stop.name,
                stop.x3794,
                stop.y3794,
                stop.geo,
                sortedLineIds
            );
            updatedStops.add(updatedStop);
        }

        return new RelationshipResult(updatedLines, updatedStops);
    }

    private static boolean isStopOnLine(BusStop stop, BusLine line, double threshold) {
        List<Geolocation> path = line.getPath();

        for (Geolocation point : path) {
            double distance = calculateDistance(stop.geo, point);
            if (distance <= threshold) {
                return true;
            }
        }

        return false;
    }

    private static double calculateDistance(Geolocation loc1, Geolocation loc2) {
        final int R = 6371000;

        double lat1Rad = Math.toRadians(loc1.lat);
        double lat2Rad = Math.toRadians(loc2.lat);
        double deltaLat = Math.toRadians(loc2.lat - loc1.lat);
        double deltaLng = Math.toRadians(loc2.lng - loc1.lng);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
            Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    private static List<BusStop> sortStopsByLineOrder(List<BusStop> stops, BusLine line) {
        final List<Geolocation> path = line.getPath();

        final Map<BusStop, Integer> stopToPathIndex = new HashMap<BusStop, Integer>();

        for (BusStop stop : stops) {
            int closestIndex = findClosestPathIndex(stop, path);
            stopToPathIndex.put(stop, closestIndex);
        }

        List<BusStop> sortedStops = new ArrayList<BusStop>(stops);
        Collections.sort(sortedStops, new Comparator<BusStop>() {
            @Override
            public int compare(BusStop s1, BusStop s2) {
                return Integer.compare(stopToPathIndex.get(s1), stopToPathIndex.get(s2));
            }
        });

        return sortedStops;
    }

    private static int findClosestPathIndex(BusStop stop, List<Geolocation> path) {
        int closestIndex = 0;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < path.size(); i++) {
            double distance = calculateDistance(stop.geo, path.get(i));
            if (distance < minDistance) {
                minDistance = distance;
                closestIndex = i;
            }
        }

        return closestIndex;
    }

    private static String getLineKey(BusLine line) {
        return line.lineId + "_" + line.variantId + "_" + line.direction;
    }

    public static class RelationshipResult {
        public final List<BusLine> lines;
        public final List<BusStop> stops;

        public RelationshipResult(List<BusLine> lines, List<BusStop> stops) {
            this.lines = lines;
            this.stops = stops;
        }
    }
}
