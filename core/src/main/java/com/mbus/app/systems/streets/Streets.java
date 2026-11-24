package com.mbus.app.systems.streets;

import com.mbus.app.utils.Geolocation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/** Where one can walk, and the addresses. Made from OpenStreetMap by the tools of this project. */
public final class Streets {

    public final WalkNetwork network;
    public final Addresses addresses;

    private Streets(WalkNetwork network, Addresses addresses) {
        this.network = network;
        this.addresses = addresses;
    }

    /** Closes the stream. */
    public static Streets load(InputStream gzip) throws IOException {
        WalkNetwork.Builder network = new WalkNetwork.Builder();
        Addresses.Builder addresses = new Addresses.Builder();
        double south = 0, west = 0;
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new GZIPInputStream(gzip, 1 << 16), StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.startsWith("#") || line.isEmpty()) continue;
                String[] parts = line.split("\\|", -1);
                switch (parts[0]) {
                    case "O":
                        long[] origin = numbers(parts[1]);
                        south = origin[0] * 1e-6;
                        west = origin[1] * 1e-6;
                        break;
                    case "N":
                        network.addNode(position(numbers(parts[1]), 0, south, west));
                        break;
                    case "E":
                        long[] edge = numbers(parts[1]);
                        List<Geolocation> between = new ArrayList<>();
                        for (int i = 2; i < edge.length; i += 2) between.add(position(edge, i, south, west));
                        network.addEdge((int) edge[0], (int) edge[1], between);
                        break;
                    case "A":
                        addresses.addAddress(parts[1], parts[2], parts[3], position(numbers(parts[4]), 0, south, west));
                        break;
                    case "S":
                        addresses.addStreet(parts[1], position(numbers(parts[2]), 0, south, west));
                        break;
                    default:
                        throw new IOException("Unknown line: " + line);
                }
            }
            return new Streets(network.build(), addresses.build());
        } catch (RuntimeException e) {
            throw new IOException("The streets are damaged", e);
        }
    }

    private static Geolocation position(long[] numbers, int index, double south, double west) {
        return new Geolocation(south + numbers[index] * 1e-6, west + numbers[index + 1] * 1e-6);
    }

    private static long[] numbers(String text) {
        String[] parts = text.split(" ");
        long[] numbers = new long[parts.length];
        for (int i = 0; i < parts.length; i++) numbers[i] = Long.parseLong(parts[i]);
        return numbers;
    }
}
