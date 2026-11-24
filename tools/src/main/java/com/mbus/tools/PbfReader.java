package com.mbus.tools;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Reads OpenStreetMap files, see https://wiki.openstreetmap.org/wiki/PBF_Format */
final class PbfReader {

    interface Handler {
        void node(long id, double lat, double lng, Map<String, String> tags);

        void way(long id, long[] nodes, Map<String, String> tags);
    }

    private static final int MAX_HEADER = 64 * 1024;
    private static final int MAX_BLOB = 32 * 1024 * 1024;

    private PbfReader() {
    }

    static void read(Path file, Handler handler) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file), 1 << 20))) {
            while (true) {
                int headerLength;
                try {
                    headerLength = in.readInt();
                } catch (EOFException end) {
                    return;
                }
                Message header = new Message(readFully(in, headerLength, MAX_HEADER));
                String type = "";
                int size = 0;
                while (header.next()) {
                    if (header.field == 1) type = header.string();
                    else if (header.field == 3) size = (int) header.varint();
                    else header.skip();
                }
                byte[] blob = readFully(in, size, MAX_BLOB);
                if (type.equals("OSMData")) readBlock(inflate(blob), handler);
            }
        }
    }

    private static byte[] readFully(InputStream in, int length, int max) throws IOException {
        if (length < 0 || length > max) throw new IOException("Not an OpenStreetMap PBF file");
        byte[] data = in.readNBytes(length);
        if (data.length < length) throw new IOException("The file is incomplete");
        return data;
    }

    private static byte[] inflate(byte[] data) throws IOException {
        Message blob = new Message(data);
        byte[] raw = null;
        byte[] packed = null;
        int size = 0;
        while (blob.next()) {
            if (blob.field == 1) raw = blob.bytes();
            else if (blob.field == 2) size = (int) blob.varint();
            else if (blob.field == 3) packed = blob.bytes();
            else blob.skip();
        }
        if (raw != null) return raw;
        if (packed == null) throw new IOException("Unknown compression");
        if (size < 0 || size > MAX_BLOB) throw new IOException("Block too large");

        Inflater inflater = new Inflater();
        try {
            inflater.setInput(packed);
            byte[] result = new byte[size];
            int read = 0;
            while (read < size) {
                int n = inflater.inflate(result, read, size - read);
                if (n == 0) throw new IOException("Damaged block");
                read += n;
            }
            return result;
        } catch (DataFormatException e) {
            throw new IOException("Damaged block", e);
        } finally {
            inflater.end();
        }
    }

    private static void readBlock(byte[] data, Handler handler) {
        String[] strings = new String[0];
        long granularity = 100, latOffset = 0, lngOffset = 0;
        Message block = new Message(data);
        while (block.next()) {
            switch (block.field) {
                case 1: strings = readStrings(block.bytes()); break;
                case 17: granularity = block.varint(); break;
                case 19: latOffset = block.varint(); break;
                case 20: lngOffset = block.varint(); break;
                default: block.skip();
            }
        }
        block = new Message(data);
        while (block.next()) {
            if (block.field != 2) {
                block.skip();
                continue;
            }
            Message group = new Message(block.bytes());
            while (group.next()) {
                if (group.field == 2) readDense(group.bytes(), strings, granularity, latOffset, lngOffset, handler);
                else if (group.field == 3) readWay(group.bytes(), strings, handler);
                else group.skip();
            }
        }
    }

    private static String[] readStrings(byte[] data) {
        List<String> strings = new ArrayList<>();
        Message table = new Message(data);
        while (table.next()) {
            if (table.field == 1) strings.add(table.string());
            else table.skip();
        }
        return strings.toArray(new String[0]);
    }

    private static void readDense(byte[] data, String[] strings, long granularity, long latOffset, long lngOffset,
                                  Handler handler) {
        Message ids = null, lats = null, lngs = null, tagIds = null;
        Message dense = new Message(data);
        while (dense.next()) {
            switch (dense.field) {
                case 1: ids = new Message(dense.bytes()); break;
                case 8: lats = new Message(dense.bytes()); break;
                case 9: lngs = new Message(dense.bytes()); break;
                case 10: tagIds = new Message(dense.bytes()); break;
                default: dense.skip();
            }
        }
        if (ids == null || lats == null || lngs == null) return;
        long id = 0, lat = 0, lng = 0;
        while (ids.hasMore()) {
            id += ids.signed();
            lat += lats.signed();
            lng += lngs.signed();
            Map<String, String> tags = Map.of();
            while (tagIds != null && tagIds.hasMore()) {
                int key = (int) tagIds.varint();
                if (key == 0) break;
                if (tags.isEmpty()) tags = new HashMap<>();
                tags.put(strings[key], strings[(int) tagIds.varint()]);
            }
            handler.node(id, 1e-9 * (latOffset + granularity * lat), 1e-9 * (lngOffset + granularity * lng), tags);
        }
    }

    private static void readWay(byte[] data, String[] strings, Handler handler) {
        long id = 0;
        Message keys = new Message(new byte[0]), values = keys;
        long[] nodes = new long[0];
        Message way = new Message(data);
        while (way.next()) {
            switch (way.field) {
                case 1: id = way.varint(); break;
                case 2: keys = new Message(way.bytes()); break;
                case 3: values = new Message(way.bytes()); break;
                case 8: nodes = readDeltas(new Message(way.bytes())); break;
                default: way.skip();
            }
        }
        Map<String, String> tags = new HashMap<>();
        while (keys.hasMore()) tags.put(strings[(int) keys.varint()], strings[(int) values.varint()]);
        handler.way(id, nodes, tags);
    }

    private static long[] readDeltas(Message message) {
        long[] result = new long[16];
        int count = 0;
        long value = 0;
        while (message.hasMore()) {
            if (count == result.length) result = Arrays.copyOf(result, 2 * count);
            value += message.signed();
            result[count++] = value;
        }
        return Arrays.copyOf(result, count);
    }

    /** A protocol buffer message, or the values of a packed field. */
    private static final class Message {
        private final byte[] data;
        private int position = 0;
        private int wireType;
        int field;

        Message(byte[] data) {
            this.data = data;
        }

        boolean hasMore() {
            return position < data.length;
        }

        boolean next() {
            if (!hasMore()) return false;
            long key = varint();
            field = (int) (key >>> 3);
            wireType = (int) (key & 7);
            return true;
        }

        long varint() {
            long result = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                byte b = data[position++];
                result |= (long) (b & 0x7f) << shift;
                if (b >= 0) return result;
            }
            throw new IllegalStateException("Damaged number");
        }

        long signed() {
            long value = varint();
            return (value >>> 1) ^ -(value & 1);
        }

        byte[] bytes() {
            int length = (int) varint();
            byte[] result = Arrays.copyOfRange(data, position, position + length);
            position += length;
            return result;
        }

        String string() {
            return new String(bytes(), StandardCharsets.UTF_8);
        }

        void skip() {
            switch (wireType) {
                case 0: varint(); break;
                case 1: position += 8; break;
                case 2: {
                    int length = (int) varint(); // moves the position itself
                    position += length;
                    break;
                }
                case 5: position += 4; break;
                default: throw new IllegalStateException("Unknown field type");
            }
        }
    }
}
