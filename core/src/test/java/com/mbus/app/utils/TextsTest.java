package com.mbus.app.utils;

import com.badlogic.gdx.files.FileHandle;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TextsTest {

    private static final LocalDate TUESDAY = LocalDate.of(2026, 11, 24);
    private final Locale computer = Locale.getDefault();

    @After
    public void restoreLocale() {
        Locale.setDefault(computer);
    }

    private static void use(String language) {
        Texts.load(new FileHandle("i18n/texts"), language);
    }

    private static Properties read(String language) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(
            Paths.get("i18n/texts_" + language + ".properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    @Test
    public void bothLanguagesHaveTheSameTextsWithTheSameArguments() throws IOException {
        Properties english = read("en");
        Properties slovene = read("sl");
        assertEquals(english.stringPropertyNames(), slovene.stringPropertyNames());
        for (String key : english.stringPropertyNames()) {
            if (key.endsWith("Date")) continue; // written differently
            assertEquals(key, arguments(english.getProperty(key)), arguments(slovene.getProperty(key)));
            assertFalse(key, slovene.getProperty(key).isEmpty());
        }
    }

    private static Set<String> arguments(String text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = Pattern.compile("\\{\\d+}").matcher(text);
        while (matcher.find()) found.add(matcher.group());
        return found;
    }

    @Test
    public void everyTextInTheCodeExists() throws IOException {
        Set<String> keys = read("en").stringPropertyNames();
        Pattern use = Pattern.compile("Texts\\.(?:get|format)\\(\"(\\w+)\"");
        try (Stream<Path> files = Files.walk(Paths.get("../core/src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).collect(Collectors.toList())) {
                Matcher matcher = use.matcher(Files.readString(file));
                while (matcher.find()) assertTrue(file + ": " + matcher.group(1), keys.contains(matcher.group(1)));
            }
        }
    }

    @Test
    public void usesTheLanguageOfTheComputerUnlessOneWasChosen() {
        Locale.setDefault(new Locale("sl", "SI"));
        use("");
        assertEquals("sl", Texts.getLanguage());
        use("en");
        assertEquals("Stops", Texts.get("stops"));

        Locale.setDefault(Locale.GERMANY);
        use("");
        assertEquals("en", Texts.getLanguage());
    }

    @Test
    public void englishDoesNotFallBackToTheComputersLanguage() {
        Locale.setDefault(new Locale("sl", "SI"));
        use("en");
        assertEquals("Departures", Texts.get("departures"));
        assertEquals("sl", Texts.getOtherLanguage());
    }

    @Test
    public void countsBuses() {
        use("sl");
        assertEquals("avtobus", Texts.buses(1));
        assertEquals("avtobusa", Texts.buses(2));
        assertEquals("avtobusi", Texts.buses(4));
        assertEquals("avtobusov", Texts.buses(5));
        assertEquals("avtobusov", Texts.buses(11));
        assertEquals("avtobus", Texts.buses(101));
        use("en");
        assertEquals("bus", Texts.buses(1));
        assertEquals("buses", Texts.buses(2));
        assertEquals("buses", Texts.buses(101));
    }

    @Test
    public void writesDates() {
        use("sl");
        assertEquals("Danes", Texts.day(TUESDAY, TUESDAY));
        assertEquals("Jutri", Texts.day(TUESDAY.plusDays(1), TUESDAY));
        assertEquals("sob., 28. 11.", Texts.day(TUESDAY.plusDays(4), TUESDAY));
        assertEquals("31. 12. 2026", Texts.date(LocalDate.of(2026, 12, 31)));
        use("en");
        assertEquals("Sat, 28 Nov", Texts.day(TUESDAY.plusDays(4), TUESDAY));
        assertEquals("31 Dec 2026", Texts.date(LocalDate.of(2026, 12, 31)));
    }

    @Test
    public void usesArgumentsAsTheyAre() {
        use("en");
        assertEquals("No results for \"it's {0}\"", Texts.format("noResults", "it's {0}"));
        assertEquals("1234 of 5678", Texts.format("countOf", 1234, 5678));
    }
}
