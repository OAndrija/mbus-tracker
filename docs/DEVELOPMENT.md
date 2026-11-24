# Development

## Shortcuts

| Input | Action |
|---|---|
| Drag / arrow keys | Move the map |
| Mouse wheel / Q, A | Zoom in and out |
| Click a stop, bus or departure | Show its departures or trip |
| Click a cluster | Zoom in on it |
| Click a line | Show only that line; click again to show all |
| Ctrl+F, then Enter / Esc | Search, open the first result / clear |
| Right-click the map | Plan a journey to there |
| J | Plan a journey |
| F | Add the open stop to the favorites, or remove it |
| T | Choose the time |
| D | Switch between light and dark |
| L | Switch between English and Slovene |
| Home | Show the whole network |
| Esc | Close the open panel |

## Architecture

```
MBusTracker (Game)
├── LoadingScreen            Loads tiles, the timetable and the streets in the background
│   ├── MapRasterTiles       Map tiles and projection
│   ├── GtfsLoader           Reads the timetable from a GTFS file
│   ├── TimetableFiles       Chooses between the bundled and a downloaded timetable
│   └── Streets              Reads the streets and addresses
└── RasterMapScreen          The app itself
    ├── TimetableUpdater     Looks for a newer timetable
    ├── JourneySearch        Runs the journey planner in the background
    │   ├── JourneyPlanner   RAPTOR over a period of departures
    │   ├── Walking          Times and ways on foot
    │   │   └── WalkNetwork  Shortest ways on the streets
    │   └── Places           Stops by name, addresses and points of the map
    │       └── Addresses    Finds streets and house numbers
    ├── MapRenderer
    │   ├── MapTileLayer     Overview plus streamed tiles
    │   ├── LineLayer        Draws the lines with a shader
    │   │   └── LineLayout   Gives every line a lane on the streets it shares
    │   ├── BusRenderer      Draws the buses
    │   │   ├── PatternMotion   Where a bus is at a given time
    │   │   └── RouteTrack      Distance along a route to position
    │   └── MarkerClusterer  Groups stops by zoom
    ├── MapUi                Everything that floats above the map
    │   ├── Sidebar          Search, lines and stops
    │   ├── StopPanel        Departures from one stop
    │   ├── TripPanel        All stops of one trip
    │   ├── JourneyPanel     Start, destination, journeys and their steps
    │   ├── TimeBar          Day and time to show
    │   └── Theme, Palette   Fonts, shapes, icons and colors
    ├── CameraController, MapGestureListener
    └── AppClock, Settings, Texts
```

## Journey planner

Walks follow the streets and paths of OpenStreetMap at 4.5 km/h; slopes and traffic lights are not considered. Outside of the known streets, a walk is the straight line and takes 30% longer. Stops up to 17 minutes from the start and the destination are used, and changes between stops up to 7 minutes apart. Changing takes at least a minute. A journey is left out if it is slower than walking, changes to save three minutes or less, or leaves a moment later only to arrive much later.

An address is found by the words of the street and a house number. If the number is not known, the closest one on that street is used, and the middle of the street if it has no numbers at all; the search says so.

## Streets and addresses

`assets/data/maribor_streets.txt.gz` comes from [OpenStreetMap](https://www.openstreetmap.org/copyright). To renew it, download `slovenia-latest.osm.pbf` (about 315 MB) from [Geofabrik](https://download.geofabrik.de/europe/slovenia.html), then run the command below and the tests. Only the surroundings of Maribor are kept, without motorways and private ways. Without the file the app still works, but has no addresses.

## Commands

| Command | Result |
|---|---|
| `./gradlew lwjgl3:run` | Runs the app |
| `./gradlew core:test` | Runs the tests |
| `./gradlew lwjgl3:jar` | JAR in `lwjgl3/build/libs` |
| `./gradlew lwjgl3:packageWinX64` | Windows package in `lwjgl3/build/construo/dist` |
| `./gradlew tools:run -Posm=path/to/slovenia-latest.osm.pbf` | Renews the streets and addresses |
| `./gradlew updateTimetable` | Renews the bundled timetable; run the tests afterwards |

## Timetable updates

Once a week the app asks GitLab, where the timetable is published, whether there is a new one; daily if the current one has expired. The request only contains the name of the app and the tag of the last download; as with any web request, the server sees your IP address. A new timetable is checked before it is used and kept in `.mbus` in your home folder.

## Releases

Set `projectVersion` in `gradle.properties`, commit, then push a tag of the same name. GitHub Actions builds the downloads and puts them into a draft release.

## Map tiles

All tiles come with the app. To download missing ones, create `core/src/main/java/com/mbus/app/utils/Keys.java` with a [Geoapify](https://www.geoapify.com) key. Without it, the app only uses the tiles it has.

```java
package com.mbus.app.utils;

public class Keys {
    public static final String GEOAPIFY = "your_key_here";
}
```
