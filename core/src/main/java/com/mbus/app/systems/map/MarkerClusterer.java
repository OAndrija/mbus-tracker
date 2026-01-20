package com.mbus.app.systems.map;

import com.badlogic.gdx.math.Vector2;
import com.mbus.app.model.BusStop;
import com.mbus.app.model.ZoomXY;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarkerClusterer {

    private static final float BASE_CLUSTER_DISTANCE = 80f;

    private static final float[] ZOOM_LEVELS = {0.0f, 0.15f, 0.3f, 0.5f, 0.8f};
    private static final float[] CLUSTER_MULTIPLIERS = {0f, 1.5f, 3.0f, 5.0f, 10.0f};

    private static Map<String, MarkerCluster> previousClusters = new HashMap<String, MarkerCluster>();
    private static int previousZoomLevel = -1;

    public static List<MarkerCluster> clusterMarkers(
        List<BusStop> stops,
        ZoomXY beginTile,
        float cameraZoom,
        int mapWidth,
        int mapHeight,
        float delta
    ) {
        if (stops == null || stops.isEmpty()) {
            return new ArrayList<MarkerCluster>();
        }

        int zoomLevel = getDiscreteZoomLevel(cameraZoom);
        float clusterDistance = BASE_CLUSTER_DISTANCE * CLUSTER_MULTIPLIERS[zoomLevel];

        boolean zoomChanged = (zoomLevel != previousZoomLevel);

        if (zoomChanged) {
            System.out.println("=== CLUSTERING DEBUG ===");
            System.out.println("Camera Zoom: " + cameraZoom);
            System.out.println("Zoom Level: " + zoomLevel + " (changed from " + previousZoomLevel + ")");
            System.out.println("Cluster Distance: " + clusterDistance);
            System.out.println("Previous Clusters Count: " + previousClusters.size());
            previousZoomLevel = zoomLevel;
        }

        List<StopWithPosition> stopsWithPos = new ArrayList<StopWithPosition>();
        for (BusStop stop : stops) {
            Vector2 pos = MapRasterTiles.getPixelPosition(
                stop.geo.lat,
                stop.geo.lng,
                beginTile.x,
                beginTile.y
            );

            if (pos.x >= 0 && pos.y >= 0 &&
                pos.x <= mapWidth && pos.y <= mapHeight) {
                stopsWithPos.add(new StopWithPosition(stop, pos));
            }
        }

        if (clusterDistance == 0f) {
            List<MarkerCluster> individualMarkers = new ArrayList<MarkerCluster>();
            for (StopWithPosition swp : stopsWithPos) {
                individualMarkers.add(new MarkerCluster(swp.position, swp.stop));
            }
            return animateTransition(individualMarkers, delta, zoomLevel, zoomChanged);
        }

        List<MarkerCluster> clusters = initialClustering(stopsWithPos, clusterDistance);

        if (zoomLevel >= 3) {
            if (zoomChanged) {
                System.out.println("Applying super-clustering...");
            }
            clusters = reclusterClusters(clusters, clusterDistance * 1.5f);
        }

        if (zoomChanged) {
            System.out.println("New Clusters Count: " + clusters.size());
        }

        return animateTransition(clusters, delta, zoomLevel, zoomChanged);
    }

    private static List<MarkerCluster> animateTransition(List<MarkerCluster> newClusters, float delta, int zoomLevel, boolean zoomChanged) {
        Map<String, MarkerCluster> newClusterMap = new HashMap<String, MarkerCluster>();

        for (MarkerCluster cluster : newClusters) {
            newClusterMap.put(cluster.getClusterId(), cluster);
        }

        List<MarkerCluster> animatedClusters = new ArrayList<MarkerCluster>();

        int existingCount = 0;
        int newCount = 0;
        int dyingCount = 0;

        for (MarkerCluster newCluster : newClusters) {
            String id = newCluster.getClusterId();

            if (previousClusters.containsKey(id)) {
                existingCount++;
                MarkerCluster existing = previousClusters.get(id);
                existing.setTarget(newCluster.position, newCluster.getCount());
                existing.stops = newCluster.stops;
                existing.isCluster = newCluster.isCluster;
                existing.updateAnimation(delta);
                animatedClusters.add(existing);
            } else {
                MarkerCluster parent = findParentCluster(newCluster);
                if (parent != null) {
                    newCount++;
                    if (zoomChanged) {
                        System.out.println("SPLIT: New cluster with " + newCluster.getCount() + " stops from parent with " + parent.getCount() + " stops");
                    }
                    newCluster.animatedPosition.set(parent.getPosition());
                    newCluster.targetPosition.set(newCluster.position);
                    newCluster.currentScale = parent.currentScale;
                    newCluster.targetScale = 1.0f;
                    newCluster.alpha = 0.8f;
                } else {
                    newCount++;
                    if (zoomChanged) {
                        System.out.println("NEW: Brand new cluster with " + newCluster.getCount() + " stops");
                    }
                    newCluster.isNew = true;
                }
                newCluster.updateAnimation(delta);
                animatedClusters.add(newCluster);
            }
        }

        for (Map.Entry<String, MarkerCluster> entry : previousClusters.entrySet()) {
            String id = entry.getKey();
            MarkerCluster oldCluster = entry.getValue();

            if (!newClusterMap.containsKey(id)) {
                dyingCount++;
                MarkerCluster mergeTarget = findMergeTarget(oldCluster, newClusters);

                if (mergeTarget != null) {
                    if (zoomChanged) {
                        System.out.println("MERGE: Cluster with " + oldCluster.getCount() + " stops merging into cluster with " + mergeTarget.getCount() + " stops (distance: " + oldCluster.getPosition().dst(mergeTarget.getPosition()) + ")");
                    }
                    oldCluster.setTarget(mergeTarget.position, mergeTarget.getCount());
                    oldCluster.targetScale = mergeTarget.currentScale > 0 ? mergeTarget.currentScale : 1.0f;
                    oldCluster.alpha = Math.max(oldCluster.alpha, 0.8f);
                } else {
                    if (zoomChanged) {
                        System.out.println("DYING: Cluster with " + oldCluster.getCount() + " stops has no merge target");
                    }
                }

                oldCluster.markForDeath();
                oldCluster.updateAnimation(delta);

                if (!oldCluster.shouldRemove()) {
                    animatedClusters.add(oldCluster);
                }
            }
        }

        if (zoomChanged) {
            System.out.println("Animation Stats - Existing: " + existingCount + ", New: " + newCount + ", Dying: " + dyingCount);
            System.out.println("Total Animated Clusters: " + animatedClusters.size());
            System.out.println("========================");
        }

        previousClusters.clear();
        for (MarkerCluster cluster : animatedClusters) {
            if (!cluster.isDying) {
                previousClusters.put(cluster.getClusterId(), cluster);
            }
        }

        return animatedClusters;
    }

    private static MarkerCluster findParentCluster(MarkerCluster newCluster) {
        for (MarkerCluster oldCluster : previousClusters.values()) {
            if (oldCluster.getStops().containsAll(newCluster.getStops())) {
                return oldCluster;
            }
        }
        return null;
    }

    private static MarkerCluster findMergeTarget(MarkerCluster oldCluster, List<MarkerCluster> newClusters) {
        MarkerCluster bestTarget = null;
        int maxOverlap = 0;

        for (MarkerCluster newCluster : newClusters) {
            int overlap = 0;
            for (BusStop stop : oldCluster.getStops()) {
                if (newCluster.getStops().contains(stop)) {
                    overlap++;
                }
            }

            if (overlap > maxOverlap) {
                maxOverlap = overlap;
                bestTarget = newCluster;
            }
        }

        if (bestTarget != null && maxOverlap > 0) {
            return bestTarget;
        }

        float minDistance = Float.MAX_VALUE;
        for (MarkerCluster newCluster : newClusters) {
            float distance = oldCluster.getPosition().dst(newCluster.getPosition());
            if (distance < minDistance) {
                minDistance = distance;
                bestTarget = newCluster;
            }
        }

        return bestTarget;
    }

    private static int getDiscreteZoomLevel(float cameraZoom) {
        for (int i = ZOOM_LEVELS.length - 1; i >= 0; i--) {
            if (cameraZoom >= ZOOM_LEVELS[i]) {
                return i;
            }
        }
        return 0;
    }

    private static List<MarkerCluster> initialClustering(
        List<StopWithPosition> stopsWithPos,
        float clusterDistance
    ) {
        List<MarkerCluster> clusters = new ArrayList<MarkerCluster>();
        boolean[] clustered = new boolean[stopsWithPos.size()];

        for (int i = 0; i < stopsWithPos.size(); i++) {
            if (clustered[i]) continue;

            StopWithPosition current = stopsWithPos.get(i);
            List<BusStop> clusterStops = new ArrayList<BusStop>();
            clusterStops.add(current.stop);

            Vector2 clusterPos = new Vector2(current.position);
            clustered[i] = true;

            for (int j = i + 1; j < stopsWithPos.size(); j++) {
                if (clustered[j]) continue;

                StopWithPosition other = stopsWithPos.get(j);

                float distance = clusterPos.dst(other.position);

                if (distance < clusterDistance) {
                    clusterStops.add(other.stop);
                    clusterPos.x = (clusterPos.x * (clusterStops.size() - 1) + other.position.x) / clusterStops.size();
                    clusterPos.y = (clusterPos.y * (clusterStops.size() - 1) + other.position.y) / clusterStops.size();
                    clustered[j] = true;
                }
            }

            clusters.add(new MarkerCluster(clusterPos, clusterStops));
        }

        return clusters;
    }

    private static List<MarkerCluster> reclusterClusters(
        List<MarkerCluster> existingClusters,
        float superClusterDistance
    ) {
        if (existingClusters.size() <= 1) {
            return existingClusters;
        }

        List<MarkerCluster> superClusters = new ArrayList<MarkerCluster>();
        boolean[] clustered = new boolean[existingClusters.size()];

        for (int i = 0; i < existingClusters.size(); i++) {
            if (clustered[i]) continue;

            MarkerCluster current = existingClusters.get(i);
            List<BusStop> allStops = new ArrayList<BusStop>(current.getStops());
            Vector2 superClusterPos = new Vector2(current.getPosition());
            clustered[i] = true;

            for (int j = i + 1; j < existingClusters.size(); j++) {
                if (clustered[j]) continue;

                MarkerCluster other = existingClusters.get(j);
                float distance = superClusterPos.dst(other.getPosition());

                if (distance < superClusterDistance) {
                    int currentCount = allStops.size();
                    allStops.addAll(other.getStops());

                    superClusterPos.x = (superClusterPos.x * currentCount + other.getPosition().x * other.getCount()) / allStops.size();
                    superClusterPos.y = (superClusterPos.y * currentCount + other.getPosition().y * other.getCount()) / allStops.size();

                    clustered[j] = true;
                }
            }

            superClusters.add(new MarkerCluster(superClusterPos, allStops));
        }

        return superClusters;
    }

    private static class StopWithPosition {
        BusStop stop;
        Vector2 position;

        StopWithPosition(BusStop stop, Vector2 position) {
            this.stop = stop;
            this.position = position;
        }
    }
}
