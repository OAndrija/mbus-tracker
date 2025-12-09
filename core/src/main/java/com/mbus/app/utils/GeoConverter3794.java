package com.mbus.app.utils;

/**
 * Converts Slovenian EPSG:3794 (D96/TM) coordinates to WGS84 latitude/longitude.
 * Works entirely offline and is accurate for all Marprom datasets.
 */
public class GeoConverter3794 {

    // Ellipsoid parameters (ETRS89/WGS84)
    private static final double a = 6378137.0;              // semi-major axis
    private static final double f = 1 / 298.257222101;      // flattening
    private static final double k0 = 0.9999;                // scale factor
    private static final double lon0 = Math.toRadians(15);  // central meridian (15°E)
    private static final double falseEasting = 500000.0;

    private static final double e2 = 2 * f - f * f;         // eccentricity squared
    private static final double e4 = e2 * e2;
    private static final double e6 = e4 * e2;

    /**
     * Convert EPSG:3794 (x, y) to WGS84 lat/lon (degrees)
     */
    public static double[] toWGS84(double x, double y) {

        // Remove false easting
        double E = x - falseEasting;
        double N = y;

        // Meridian arc
        double M = N / k0;

        // Footpoint latitude
        double mu = M / (a * (1 - e2 / 4 - 3 * e4 / 64 - 5 * e6 / 256));

        double e1 = (1 - Math.sqrt(1 - e2)) / (1 + Math.sqrt(1 - e2));

        double J1 = 3 * e1 / 2 - 27 * Math.pow(e1, 3) / 32;
        double J2 = 21 * e1 * e1 / 16 - 55 * Math.pow(e1, 4) / 32;
        double J3 = 151 * Math.pow(e1, 3) / 96;
        double J4 = 1097 * Math.pow(e1, 4) / 512;

        double fp = mu
            + J1 * Math.sin(2 * mu)
            + J2 * Math.sin(4 * mu)
            + J3 * Math.sin(8 * mu)
            + J4 * Math.sin(16 * mu);

        // Radius of curvature values
        double sinFp = Math.sin(fp);
        double cosFp = Math.cos(fp);

        double ePrime2 = e2 / (1 - e2);

        double C1 = ePrime2 * cosFp * cosFp;
        double T1 = Math.tan(fp) * Math.tan(fp);
        double R1 = a * (1 - e2) / Math.pow(1 - e2 * sinFp * sinFp, 1.5);
        double N1 = a / Math.sqrt(1 - e2 * sinFp * sinFp);

        double D = E / (N1 * k0);

        // Latitude
        double lat = fp
            - (N1 * Math.tan(fp) / R1) *
            (D * D / 2
                - (5 + 3 * T1 + 10 * C1 - 4 * C1 * C1 - 9 * ePrime2) * Math.pow(D, 4) / 24
                + (61 + 90 * T1 + 298 * C1 + 45 * T1 * T1 - 252 * ePrime2 - 3 * C1 * C1) * Math.pow(D, 6) / 720
            );

        // Longitude
        double lon = lon0 +
            (D
                - (1 + 2 * T1 + C1) * Math.pow(D, 3) / 6
                + (5 - 2 * C1 + 28 * T1 - 3 * C1 * C1 + 8 * ePrime2 + 24 * T1 * T1) * Math.pow(D, 5) / 120
            ) / cosFp;

        return new double[]{
            Math.toDegrees(lat),
            Math.toDegrees(lon)
        };
    }
}
