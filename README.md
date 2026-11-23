# MBus — Maribor Bus Tracker

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)
![LibGDX](https://img.shields.io/badge/LibGDX-1.13.1-red?logo=libgdx)
![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20macOS-blue)
![License](https://img.shields.io/badge/License-MIT-green)
![Release](https://img.shields.io/github/v/release/OAndrija/mbus-tracker?include_prereleases)

A desktop bus tracking application for the city of Maribor, Slovenia, built with **Java** and **LibGDX**. MBus visualizes local bus lines and stops on an interactive map, with animated real-time bus position simulation based on schedule data.

![Overview](docs/images/bus.png)

---

## Download

| File                     | Description |
|--------------------------|-------------|
| `mbus-vX.X.X-winX64.zip` | Windows 64-bit — no Java required, just extract and run the .exe |
| `mbus-vX.X.X.jar`        | Cross-platform — requires Java 17+ |

See the [Releases](../../releases) page to download the latest version.

---

## Features

- **Interactive map** rendered from raster tiles (Geoapify / OpenStreetMap), with tile caching for offline use
- **Bus line visualization** with per-line color coding, hover effects, and animated selection
- **Bus stop markers** with dynamic clustering that adapts to zoom level
- **Animated bus positions** for every visible line, calculated from schedule data: buses accelerate, brake at stops and turn along the road
- **Floating panels** for filtering lines and stops, with a searchable list (accents and case are ignored) and upcoming arrivals per stop
- **Streamed map tiles** — only the visible area is kept in memory at full resolution
- **Smooth camera** with drag-to-pan and zoom at the cursor, clamped to the map bounds, and animated fly-to on stop selection
- **Background loading screen** with threaded tile downloads and data parsing, showing live progress
- **Offline-first design** — ships with a pre-built tile cache; API key is optional and loaded gracefully via reflection if absent

---

## Screenshots

![Overview](docs/images/overview.png)
![Line Selection](docs/images/line_selection.png)

---

## Controls

| Input | Action |
|---|---|
| Drag / arrow keys | Move the map |
| Mouse wheel / Q, A | Zoom in and out |
| Click a stop | Show upcoming arrivals |
| Click a cluster | Zoom in on it |
| Click a line | Show only that line; click again to show all |
| Enter / Esc in the search box | Open the first result / clear the search |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | LibGDX |
| Rendering | OrthographicCamera, SpriteBatch, ShapeRenderer, Texture streaming |
| Map tiles | Geoapify REST API (OSM Bright) |
| Map data | GeoJSON (bus stops and lines from public Maribor transit data) |
| UI | Scene2D with a custom theme; FreeType for text |
| Build | Gradle (Desktop target via LWJGL3 + Construo) |

---

## Architecture

```
MBusTracker (Game)
├── LoadingScreen          — Threaded tile download + GeoJSON parsing
│   ├── MapRasterTiles     — Tile fetch, cache read/write, coordinate math
│   ├── GeoJSONLoader      — Parses bus stop and line geometry
│   └── ScheduleLoader     — Loads or generates bus schedules (the bundled ones are generated, not the official timetable)
└── RasterMapScreen        — Main application screen
    ├── MapTileLayer       — Low-resolution overview plus streamed full-resolution tiles
    ├── MapRenderer        — Bus lines, stop markers, labels
    ├── BusRenderer        — Draws the buses
    │   ├── LineMotion      — Where a bus is at a given time
    │   └── RouteTrack      — Distance along a route to position
    ├── MarkerClusterer    — Zoom-aware stop clustering with animations
    ├── MapUi              — Layout of everything that floats above the map
    │   ├── Sidebar         — Search, line filter and stop list
    │   ├── StopPanel       — Upcoming arrivals at one stop
    │   └── Theme           — Fonts, colors, shapes and icons, rendered for the screen's density
    └── Input
        ├── MapGestureListener  — Pan, zoom, tap handling
        └── BusLineClickHandler — Line segment hit testing
```

---

## Getting Started (Developers)

### Prerequisites

- Java 17+
- Gradle (wrapper included)

### Clone the repository

```bash
git clone https://github.com/OAndrija/mbus-tracker.git
cd mbus-tracker
```

### Run from source

```bash
./gradlew lwjgl3:run
```

### Run the tests

```bash
./gradlew core:test
```

### Build a distributable JAR

```bash
./gradlew lwjgl3:jar
```

Output: `lwjgl3/build/libs/`

### Build a Windows executable

```bash
./gradlew lwjgl3:packageWinX64
```

Output: `lwjgl3/build/construo/`

The JAR and EXE both include all assets and the pre-cached map tiles, so no network connection or API key is required for normal use.

### API Key (optional)

If you want the app to re-download tiles on cache miss, create `core/src/main/java/com/mbus/app/utils/Keys.java`:

```java
public class Keys {
    public static final String GEOAPIFY = "your_key_here";
}
```

If this file is absent the app runs in cache-only mode — no errors, no crashes.

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
