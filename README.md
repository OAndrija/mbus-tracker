# MBus — Maribor Bus Tracker

A desktop bus tracking application for the city of Maribor, Slovenia, built with **Java** and **LibGDX**. MBus visualizes local bus lines and stops on an interactive map, with animated real-time bus position simulation based on schedule data.

---

## Features

- **Interactive map** rendered from raster tiles (Geoapify / OpenStreetMap), with tile caching for offline use
- **Bus line visualization** with per-line color coding, hover effects, and animated selection
- **Bus stop markers** with dynamic clustering that adapts to zoom level
- **Animated bus positions** calculated from schedule data and rendered directionally (8-direction sprites)
- **HUD panel** for filtering lines and stops, with a searchable list and per-stop detail view
- **Smooth camera** with gesture-based pan/zoom, clamped to the map bounds, and animated fly-to on stop selection
- **Background loading screen** with threaded tile downloads and data parsing, showing live progress
- **Offline-first design** — ships with a pre-built tile cache; API key is optional and loaded gracefully via reflection if absent

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 11 |
| Framework | LibGDX |
| Rendering | OrthographicCamera, SpriteBatch, ShapeRenderer, TiledMap |
| Map tiles | Geoapify REST API (OSM Bright) |
| Map data | GeoJSON (bus stops and lines from public Maribor transit data) |
| UI | Scene2D (Stage, Table, Skin) |
| Build | Gradle (Desktop target via LWJGL3) |

---

## Architecture

```
MBusTracker (Game)
├── LoadingScreen          — Threaded tile download + GeoJSON parsing
│   ├── MapRasterTiles     — Tile fetch, cache read/write, coordinate math
│   ├── GeoJSONLoader      — Parses bus stop and line geometry
│   └── ScheduleLoader     — Loads or generates bus schedules
└── RasterMapScreen        — Main application screen
    ├── MapRenderer        — Tile map, bus lines, stop markers, labels
    ├── BusAnimationRenderer — Interpolated bus position rendering
    ├── MarkerClusterer    — Zoom-aware stop clustering with animations
    ├── HudPanel           — Line/stop filter sidebar
    ├── BusStopDetailPanel — Per-stop schedule detail view
    └── Input
        ├── MapGestureListener  — Pan, zoom, tap handling
        ├── MarkerClickHandler  — Stop hit testing in world space
        └── BusLineClickHandler — Line segment hit testing
```

---

## Getting Started

### Prerequisites

- Java 11+
- Gradle (wrapper included)

### Run

```bash
./gradlew lwjgl3:run
```

### Build a distributable JAR

```bash
./gradlew lwjgl3:jar
```

The output JAR includes all assets and the pre-cached map tiles, so no network connection or API key is required for normal use.

### API Key (optional)

If you want the app to re-download tiles on cache miss, create `core/src/main/java/com/mbus/app/utils/Keys.java`:

```java
public class Keys {
    public static final String GEOAPIFY = "your_key_here";
}
```

If this file is absent the app runs in cache-only mode — no errors, no crashes.

---

## Data Sources

- **Bus stops and lines** — Public GeoJSON data from the City of Maribor / Marprom
- **Schedules** — Generated from route data; real schedule import supported via `schedules.json`
