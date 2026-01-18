package com.mbus.app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BusStop {

    public final int idAvpost;
    public final String idMarprom;
    public final String name;

    public final double x3794;
    public final double y3794;

    public final Geolocation geo;

    private final List<Integer> lineIds;

    public BusStop(int idAvpost,
                   String idMarprom,
                   String name,
                   double x3794,
                   double y3794,
                   Geolocation geo,
                   List<Integer> lineIds) {

        this.idAvpost = idAvpost;
        this.idMarprom = idMarprom;
        this.name = name;

        this.x3794 = x3794;
        this.y3794 = y3794;

        this.geo = geo;

        this.lineIds = lineIds != null
            ? Collections.unmodifiableList(new ArrayList<Integer>(lineIds))
            : Collections.unmodifiableList(new ArrayList<Integer>());
    }

    public BusStop(int idAvpost,
                   String idMarprom,
                   String name,
                   double x3794,
                   double y3794,
                   Geolocation geo) {
        this(idAvpost, idMarprom, name, x3794, y3794, geo, null);
    }

    public List<Integer> getLineIds() {
        return lineIds;
    }

    public int getLineCount() {
        return lineIds.size();
    }

    public boolean hasLine(int lineId) {
        return lineIds.contains(lineId);
    }

    public String getLineIdsString() {
        if (lineIds.isEmpty()) {
            return "No lines";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lineIds.size(); i++) {
            sb.append(lineIds.get(i));
            if (i < lineIds.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    public List<StopArrival> getUpcomingArrivals(List<BusLine> lines, int currentTime, int dayType, int maxResults) {
        List<StopArrival> arrivals = new ArrayList<StopArrival>();

        for (BusLine line : lines) {
            if (!hasLine(line.lineId)) continue;

            for (BusSchedule schedule : line.getSchedules()) {
                if (schedule.dayType != dayType) continue;

                int arrivalTime = schedule.getArrivalTimeAtStop(idAvpost);
                if (arrivalTime >= currentTime) {
                    arrivals.add(new StopArrival(line, schedule, arrivalTime));
                }
            }
        }

        Collections.sort(arrivals, new Comparator<StopArrival>() {
            @Override
            public int compare(StopArrival a1, StopArrival a2) {
                return Integer.compare(a1.arrivalTime, a2.arrivalTime);
            }
        });

        if (arrivals.size() > maxResults) {
            return arrivals.subList(0, maxResults);
        }

        return arrivals;
    }

    @Override
    public String toString() {
        return "BusStop{" +
            "idAvpost=" + idAvpost +
            ", idMarprom='" + idMarprom + '\'' +
            ", name='" + name + '\'' +
            ", x3794=" + x3794 +
            ", y3794=" + y3794 +
            ", geo=(" + geo.lat + ", " + geo.lng + ")" +
            ", lines=" + getLineIdsString() +
            '}';
    }

    public static class StopArrival {
        public final BusLine line;
        public final BusSchedule schedule;
        public final int arrivalTime;

        public StopArrival(BusLine line, BusSchedule schedule, int arrivalTime) {
            this.line = line;
            this.schedule = schedule;
            this.arrivalTime = arrivalTime;
        }

        public int getMinutesUntilArrival(int currentTime) {
            return arrivalTime - currentTime;
        }

        public String getArrivalTimeFormatted() {
            return BusSchedule.formatTime(arrivalTime);
        }

        @Override
        public String toString() {
            return "Line " + line.lineId + " at " + getArrivalTimeFormatted();
        }
    }
}
