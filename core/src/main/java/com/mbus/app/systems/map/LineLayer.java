package com.mbus.app.systems.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.mbus.app.model.BusLine;
import com.mbus.app.model.RoutePattern;
import com.mbus.app.utils.Display;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Draws the lines side by side. Lanes are worked out in the background whenever other lines are shown;
 * until they are ready, the previous lanes are used. The graphics card computes the positions, so
 * zooming and panning cost nothing.
 */
public class LineLayer implements Disposable {

    private static final String VERTEX_SHADER = ""
        + "attribute vec2 a_position;\n"
        + "attribute vec2 a_normal;\n"
        + "attribute vec3 a_lane;\n" // lane, number of lanes, side
        + "uniform mat4 u_projTrans;\n"
        + "uniform float u_pixel;\n"
        + "uniform float u_width;\n"
        + "uniform float u_gap;\n"
        + "uniform float u_band;\n"
        + "uniform float u_extra;\n"
        + "varying float v_side;\n"
        + "varying float v_half;\n"
        + "void main() {\n"
        + "  float spacing = min(u_gap, u_band / max(1.0, a_lane.y));\n"
        + "  float width = a_lane.y < 1.5 ? u_width : min(u_width, max(1.2, spacing));\n"
        + "  v_half = (width + u_extra) * 0.5;\n"
        + "  v_side = a_lane.z * (v_half + 1.0);\n"
        + "  vec2 position = a_position + a_normal * (a_lane.x * spacing + v_side) * u_pixel;\n"
        + "  gl_Position = u_projTrans * vec4(position, 0.0, 1.0);\n"
        + "}\n";

    private static final String FRAGMENT_SHADER = ""
        + "#ifdef GL_ES\n"
        + "precision mediump float;\n"
        + "#endif\n"
        + "uniform vec4 u_color;\n"
        + "uniform float u_density;\n"
        + "varying float v_side;\n"
        + "varying float v_half;\n"
        + "void main() {\n"
        + "  float coverage = clamp(0.5 + (v_half - abs(v_side)) * u_density, 0.0, 1.0);\n"
        + "  gl_FragColor = vec4(u_color.rgb, u_color.a * coverage);\n"
        + "}\n";

    private static final int FLOATS = 7;

    private final WorldGeometry geometry;
    private final ShaderProgram shader;
    private final ExecutorService executor;

    private Map<RoutePattern, Strand> strands = Collections.emptyMap();
    private final Map<BusLine, Mesh> meshes = new HashMap<>();
    private RoutePattern singlePattern;
    private Mesh singleMesh;
    private int requests = 0;
    private boolean disposed = false;

    public LineLayer(WorldGeometry geometry) {
        this.geometry = geometry;
        shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!shader.isCompiled()) throw new GdxRuntimeException("Line shader: " + shader.getLog());
        executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "line-layout");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Gives the lines their lanes. */
    public void setLines(Collection<BusLine> lines) {
        final int request = ++requests;
        final TreeSet<BusLine> sorted = new TreeSet<>(lines);
        executor.execute(() -> {
            final Map<RoutePattern, Strand> result = layOut(sorted);
            Gdx.app.postRunnable(() -> {
                if (request == requests && !disposed) use(result);
            });
        });
    }

    private Map<RoutePattern, Strand> layOut(TreeSet<BusLine> lines) {
        List<RoutePattern> patterns = new ArrayList<>();
        List<LineLayout.Road> roads = new ArrayList<>();
        int rank = 0;
        for (BusLine line : lines) {
            for (RoutePattern pattern : line.getPatterns()) {
                WorldGeometry.Path path = geometry.getPattern(pattern);
                patterns.add(pattern);
                roads.add(new LineLayout.Road(rank, path.x, path.y));
            }
            rank++;
        }
        List<Strand> built = LineLayout.build(roads);
        Map<RoutePattern, Strand> result = new IdentityHashMap<>();
        for (int i = 0; i < patterns.size(); i++) result.put(patterns.get(i), built.get(i));
        return result;
    }

    private void use(Map<RoutePattern, Strand> newStrands) {
        strands = newStrands;
        for (Mesh mesh : meshes.values()) mesh.dispose();
        meshes.clear();
        Map<BusLine, List<Strand>> byLine = new HashMap<>();
        for (Map.Entry<RoutePattern, Strand> entry : strands.entrySet()) {
            byLine.computeIfAbsent(entry.getKey().line, l -> new ArrayList<>()).add(entry.getValue());
        }
        for (Map.Entry<BusLine, List<Strand>> entry : byLine.entrySet()) {
            meshes.put(entry.getKey(), createMesh(entry.getValue()));
        }
        clearSingle();
    }

    private static Mesh createMesh(List<Strand> strands) {
        int points = 0;
        int segments = 0;
        for (Strand strand : strands) {
            points += strand.size();
            segments += strand.size() - 1;
        }
        float[] vertices = new float[points * 2 * FLOATS];
        short[] indices = new short[segments * 6];
        int v = 0;
        int t = 0;
        int vertex = 0;
        for (Strand strand : strands) {
            for (int i = 0; i < strand.size(); i++) {
                for (int side = -1; side <= 1; side += 2) {
                    vertices[v++] = strand.x[i];
                    vertices[v++] = strand.y[i];
                    vertices[v++] = strand.normalX[i];
                    vertices[v++] = strand.normalY[i];
                    vertices[v++] = strand.lane[i];
                    vertices[v++] = strand.laneCount[i];
                    vertices[v++] = side;
                }
                if (i > 0) {
                    int a = vertex + 2 * i - 2;
                    indices[t++] = (short) a;
                    indices[t++] = (short) (a + 1);
                    indices[t++] = (short) (a + 2);
                    indices[t++] = (short) (a + 1);
                    indices[t++] = (short) (a + 3);
                    indices[t++] = (short) (a + 2);
                }
            }
            vertex += 2 * strand.size();
        }
        Mesh mesh = new Mesh(true, vertices.length / FLOATS, indices.length,
            new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_normal"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 3, "a_lane"));
        mesh.setVertices(vertices);
        mesh.setIndices(indices);
        return mesh;
    }

    public void begin(Matrix4 projection, LineStyle style) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shader.bind();
        shader.setUniformMatrix("u_projTrans", projection);
        shader.setUniformf("u_pixel", style.pixel);
        shader.setUniformf("u_width", style.width);
        shader.setUniformf("u_gap", style.gap);
        shader.setUniformf("u_band", style.maxBand);
        shader.setUniformf("u_density", Display.getDensity());
    }

    /** Draws a line, some pixels wider than usual. */
    public void draw(BusLine line, Color color, float extraWidth) {
        draw(meshes.get(line), color, extraWidth);
    }

    /** Draws only one way a line is driven, even if the line is not shown. */
    public void draw(RoutePattern pattern, Color color, float extraWidth) {
        if (singlePattern != pattern) {
            clearSingle();
            singlePattern = pattern;
            singleMesh = createMesh(Collections.singletonList(getStrandOrRoute(pattern)));
        }
        draw(singleMesh, color, extraWidth);
    }

    private Strand getStrandOrRoute(RoutePattern pattern) {
        Strand strand = strands.get(pattern);
        if (strand != null) return strand;
        WorldGeometry.Path path = geometry.getPattern(pattern);
        return LineLayout.build(Collections.singletonList(new LineLayout.Road(0, path.x, path.y))).get(0);
    }

    private void draw(Mesh mesh, Color color, float extraWidth) {
        if (mesh == null) return;
        shader.setUniformf("u_color", color);
        shader.setUniformf("u_extra", extraWidth);
        mesh.render(shader, GL20.GL_TRIANGLES);
    }

    /** How a route is drawn, or null if its line is not shown. */
    public Strand getStrand(RoutePattern pattern) {
        return strands.get(pattern);
    }

    /** The line drawn closest to a world position, or null if there is none within a distance. */
    public BusLine findLine(float px, float py, float maxDistance, Collection<BusLine> lines, LineStyle style) {
        float reach = maxDistance + style.maxBand * style.pixel;
        float closest = maxDistance * maxDistance;
        BusLine found = null;
        for (BusLine line : lines) {
            for (RoutePattern pattern : line.getPatterns()) {
                Strand strand = strands.get(pattern);
                if (strand == null
                    || px < strand.bounds.x - reach || px > strand.bounds.x + strand.bounds.width + reach
                    || py < strand.bounds.y - reach || py > strand.bounds.y + strand.bounds.height + reach) continue;

                for (int i = 0; i + 1 < strand.size(); i++) {
                    if (Math.abs(strand.x[i] - px) > reach + 40f || Math.abs(strand.y[i] - py) > reach + 40f) continue;
                    float distance = segmentDistance(px, py,
                        strand.drawnX(i, style), strand.drawnY(i, style),
                        strand.drawnX(i + 1, style), strand.drawnY(i + 1, style));
                    if (distance < closest) {
                        closest = distance;
                        found = line;
                    }
                }
            }
        }
        return found;
    }

    private static float segmentDistance(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float lengthSquared = dx * dx + dy * dy;
        float t = lengthSquared == 0 ? 0 : ((px - x1) * dx + (py - y1) * dy) / lengthSquared;
        t = Math.max(0f, Math.min(1f, t));
        float ex = px - x1 - t * dx;
        float ey = py - y1 - t * dy;
        return ex * ex + ey * ey;
    }

    private void clearSingle() {
        if (singleMesh != null) singleMesh.dispose();
        singleMesh = null;
        singlePattern = null;
    }

    @Override
    public void dispose() {
        disposed = true;
        executor.shutdownNow();
        clearSingle();
        for (Mesh mesh : meshes.values()) mesh.dispose();
        meshes.clear();
        shader.dispose();
    }
}
