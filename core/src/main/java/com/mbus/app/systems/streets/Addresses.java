package com.mbus.app.systems.streets;

import com.mbus.app.utils.Geolocation;
import com.mbus.app.utils.StopSearch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/** Finds streets and house numbers. */
public final class Addresses {

    private static final double METERS_PER_DEGREE = 111_320;
    private static final double LNG_SCALE = Math.cos(Math.toRadians(46.55));
    /** Streets of the same name further apart than this are different ones. */
    private static final double SAME_STREET_METERS = 500;

    /** An address, or a street if it has no number. */
    public static final class Address {
        public final String street, number, city;
        public final Geolocation geo;

        private Address(String street, String number, String city, Geolocation geo) {
            this.street = street;
            this.number = number;
            this.city = city;
            this.geo = geo;
        }

        public String getName() {
            return number.isEmpty() ? street : street + " " + number;
        }
    }

    /** A found address. */
    public static final class Match {
        public final Address address;
        /** The number asked for if it is not known and this is the closest one, otherwise empty. */
        public final String insteadOf;

        private Match(Address address, String insteadOf) {
            this.address = address;
            this.insteadOf = insteadOf;
        }
    }

    private static final class Street {
        final String normalized;
        final String[] words;
        String[] cityWords;
        final List<Address> numbers = new ArrayList<>();
        Address middle;

        Street(String name) {
            normalized = StopSearch.normalize(name);
            words = words(name);
        }

        boolean isNear(Geolocation geo) {
            if (middle != null) return meters(middle.geo, geo) < SAME_STREET_METERS;
            for (Address address : numbers) {
                if (meters(address.geo, geo) < SAME_STREET_METERS) return true;
            }
            return false;
        }
    }

    static final class Builder {
        private final Map<String, List<Street>> byName = new HashMap<>();

        private List<Street> named(String name) {
            return byName.computeIfAbsent(StopSearch.normalize(name), n -> new ArrayList<>());
        }

        private Street find(String name, Geolocation geo) {
            List<Street> streets = named(name);
            Street found = null;
            for (Street street : new ArrayList<>(streets)) {
                if (!street.isNear(geo)) continue;
                if (found == null) {
                    found = street;
                } else { // turns out to be the same one
                    found.numbers.addAll(street.numbers);
                    streets.remove(street);
                }
            }
            if (found == null) {
                found = new Street(name);
                streets.add(found);
            }
            return found;
        }

        void addAddress(String street, String number, String city, Geolocation geo) {
            find(street, geo).numbers.add(new Address(street, number, city, geo));
        }

        void addStreet(String name, Geolocation geo) {
            Street street = new Street(name);
            street.middle = new Address(name, "", "", geo);
            named(name).add(street);
        }

        Addresses build() {
            List<Street> streets = new ArrayList<>();
            for (List<Street> sameName : byName.values()) streets.addAll(mergeTowns(sameName));
            for (Street street : streets) {
                if (street.middle == null) street.middle = middleOf(street);
                street.cityWords = words(street.middle.city);
                street.numbers.sort((a, b) -> {
                    int byNumber = Integer.compare(digits(a.number), digits(b.number));
                    return byNumber != 0 ? byNumber : a.number.compareToIgnoreCase(b.number);
                });
            }
            streets.sort((a, b) -> {
                int byName = a.normalized.compareTo(b.normalized);
                return byName != 0 ? byName : Integer.compare(b.numbers.size(), a.numbers.size());
            });
            return new Addresses(streets);
        }

        /** A town has no two streets of the same name, however long the gap between their houses. */
        private static List<Street> mergeTowns(List<Street> sameName) {
            Map<String, Street> byTown = new HashMap<>();
            List<Street> merged = new ArrayList<>();
            for (Street street : sameName) {
                String town = street.numbers.isEmpty() ? "" : mostCommon(street.numbers, address -> address.city);
                Street other = town.isEmpty() ? null : byTown.putIfAbsent(town, street);
                if (other == null) merged.add(street);
                else other.numbers.addAll(street.numbers);
            }
            return merged;
        }

        /** Empty texts only count if there are no others. */
        private static String mostCommon(List<Address> addresses, Function<Address, String> text) {
            Map<String, Integer> counts = new TreeMap<>();
            for (Address address : addresses) {
                if (!text.apply(address).isEmpty()) counts.merge(text.apply(address), 1, Integer::sum);
            }
            return counts.isEmpty() ? "" : Collections.max(counts.entrySet(), Map.Entry.comparingByValue()).getKey();
        }

        /** The address closest to the average of all, so that it is on the street. */
        private static Address middleOf(Street street) {
            double lat = 0, lng = 0;
            for (Address address : street.numbers) {
                lat += address.geo.lat / street.numbers.size();
                lng += address.geo.lng / street.numbers.size();
            }
            Geolocation center = new Geolocation(lat, lng);
            Address closest = street.numbers.get(0);
            for (Address address : street.numbers) {
                if (meters(address.geo, center) < meters(closest.geo, center)) closest = address;
            }
            return new Address(mostCommon(street.numbers, address -> address.street), "",
                mostCommon(street.numbers, address -> address.city), closest.geo);
        }
    }

    private final List<Street> streets;

    private Addresses(List<Street> streets) {
        this.streets = streets;
    }

    private static double meters(Geolocation a, Geolocation b) {
        return METERS_PER_DEGREE * Math.hypot(a.lat - b.lat, (a.lng - b.lng) * LNG_SCALE);
    }

    /** In lower case and without accents, such as "ulica" "10" "oktobra". */
    private static String[] words(String text) {
        return StopSearch.normalize(text).trim().split("[^\\p{L}\\p{N}]+");
    }

    /** The number without letters, such as 12 of "12a". */
    private static int digits(String number) {
        int end = 0;
        while (end < number.length() && end < 6 && Character.isDigit(number.charAt(end))) end++;
        return end == 0 ? -1 : Integer.parseInt(number.substring(0, end));
    }

    /**
     * Streets whose words start with the words of a query, best first. A number is looked up on them, and
     * the name of the town may be part of the query. Upper case and accents do not matter.
     */
    public List<Match> search(String query, int max) {
        List<String> all = new ArrayList<>(Arrays.asList(words(query)));
        all.removeIf(String::isEmpty);
        if (query.trim().length() < 2 || all.isEmpty() || Character.isDigit(all.get(0).charAt(0))) {
            return Collections.emptyList();
        }
        List<String> words = new ArrayList<>(all);
        String number = takeNumber(words);

        // By whether the number is known, then by whether the name starts like the query
        List<List<Match>> ranks = new ArrayList<>();
        for (int i = 0; i < 4; i++) ranks.add(new ArrayList<>());
        for (Street street : streets) {
            Match match;
            // First without, as there is an Ulica 10. oktobra
            if (matches(street, all)) match = lookUp(street, "");
            else if (!number.isEmpty() && matches(street, words)) match = lookUp(street, number);
            else continue;
            boolean exact = !match.address.number.isEmpty() && match.insteadOf.isEmpty();
            ranks.get((exact ? 0 : 2) + (street.normalized.startsWith(all.get(0)) ? 0 : 1)).add(match);
        }
        List<Match> found = new ArrayList<>();
        for (List<Match> rank : ranks) found.addAll(rank);
        return found.size() > max ? found.subList(0, max) : found;
    }

    /** True if there is a house number in a query, such as in "Glavni trg 5". */
    public static boolean hasNumber(String query) {
        List<String> words = new ArrayList<>(Arrays.asList(words(query)));
        return words.size() > 1 && !takeNumber(words).isEmpty();
    }

    /** Removes the last house number, such as "10a" or "10 a", and returns it. Empty if there is none. */
    private static String takeNumber(List<String> words) {
        for (int i = words.size() - 1; i > 0; i--) {
            if (words.get(i).matches("\\d{1,4}[a-z]?")) {
                String number = words.remove(i);
                boolean letter = Character.isDigit(number.charAt(number.length() - 1))
                    && i < words.size() && words.get(i).matches("[a-z]");
                return letter ? number + words.remove(i) : number;
            }
        }
        return "";
    }

    private static boolean matches(Street street, List<String> words) {
        if (!startsWith(street.words, words.get(0))) return false;
        for (String word : words) {
            if (!startsWith(street.words, word) && !startsWith(street.cityWords, word)) return false;
        }
        return true;
    }

    private static boolean startsWith(String[] words, String start) {
        for (String word : words) {
            if (word.startsWith(start)) return true;
        }
        return false;
    }

    private static Match lookUp(Street street, String number) {
        if (number.isEmpty() || street.numbers.isEmpty()) return new Match(street.middle, number);
        int wanted = digits(number);
        Address closest = null;
        for (Address address : street.numbers) {
            if (StopSearch.normalize(address.number).replace(" ", "").equals(number)) return new Match(address, "");
            if (closest == null || distance(address, wanted) < distance(closest, wanted)) closest = address;
        }
        return new Match(closest, number);
    }

    /** Numbers on the other side of the street count as a little further away. */
    private static int distance(Address address, int wanted) {
        int difference = Math.abs(digits(address.number) - wanted);
        return 2 * difference + difference % 2;
    }

    /** The closest address with a number within a distance, or null. */
    public Address findNear(Geolocation geo, double maxMeters) {
        Address closest = null;
        double limit = maxMeters;
        for (Street street : streets) {
            for (Address address : street.numbers) {
                if (Math.abs(address.geo.lat - geo.lat) * METERS_PER_DEGREE > limit) continue;
                double distance = meters(address.geo, geo);
                if (distance < limit) {
                    limit = distance;
                    closest = address;
                }
            }
        }
        return closest;
    }
}
