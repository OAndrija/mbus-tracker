package com.mbus.app;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.mbus.app.model.Timetable;
import com.mbus.app.screens.LoadingScreen;
import com.mbus.app.systems.input.CameraController;
import com.mbus.app.ui.theme.Palette;
import com.mbus.app.ui.theme.Theme;
import com.mbus.app.utils.Constants;
import com.mbus.app.utils.Settings;

public class MBusTracker extends Game {
    public OrthographicCamera camera;
    public Viewport viewport;
    public CameraController cameraController;
    public InputMultiplexer inputMultiplexer;
    private Theme theme;
    private Settings settings;

    private Timetable timetable;

    @Override
    public void create() {
        Gdx.app.setLogLevel(Application.LOG_ERROR);

        camera = new OrthographicCamera();

        viewport = new ExtendViewport(Constants.MAP_WIDTH, Constants.MAP_HEIGHT, camera);
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

        settings = new Settings(Gdx.app.getPreferences("mbus"));
        Palette.setDark(settings.isDark());
        theme = new Theme();

        camera.zoom = 0.20f;
        camera.position.set(Constants.MAP_WIDTH / 2f, Constants.MAP_HEIGHT / 2f, 0);
        camera.update();

        cameraController = new CameraController(camera);

        inputMultiplexer = new InputMultiplexer();
        Gdx.input.setInputProcessor(inputMultiplexer);

        setScreen(new LoadingScreen(this));
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, false);
        super.resize(width, height);
    }

    @Override
    public void dispose() {
        super.dispose(); // only hides the current screen
        if (getScreen() != null) getScreen().dispose();
        if (theme != null) theme.dispose();
    }

    public Theme getTheme() {
        return theme;
    }

    /** Switches between the light and the dark theme. Returns the previous theme, to be disposed once it is not used any more. */
    public Theme switchTheme(boolean dark) {
        settings.setDark(dark);
        Palette.setDark(dark);
        Theme previous = theme;
        theme = new Theme();
        return previous;
    }

    public Settings getSettings() {
        return settings;
    }

    public void setTimetable(Timetable timetable) {
        this.timetable = timetable;
    }

    public Timetable getTimetable() {
        return timetable;
    }
}
