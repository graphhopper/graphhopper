/*
 * Copyright (c) 2015, Conveyal
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.conveyal.gtfs.model;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.error.*;
import com.conveyal.gtfs.util.CsvReader;
import org.apache.commons.io.input.BOMInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * An abstract base class that represents a row in a GTFS table, e.g. a Stop, Trip, or Agency.
 * One concrete subclass is defined for each table in a GTFS feed.
 */
// TODO K is the key type for this table
public abstract class Entity implements Serializable, Cloneable {

    private static final long serialVersionUID = -3576441868127607448L;
    public static final int INT_MISSING = Integer.MIN_VALUE;
    public long sourceFileLine;

    /* The feed from which this entity was loaded. */
    transient GTFSFeed feed;

    /* A class that can produce Entities from CSV, and record errors that occur in the process. */
    // This is almost a GTFSTable... rename?
    public static abstract class Loader<E extends Entity> {

        private static final Logger LOG = LoggerFactory.getLogger(Loader.class);

        protected final GTFSFeed feed;    // the feed into which we are loading the entities
        protected final String tableName; // name of corresponding table without .txt
        protected final Set<String> missingRequiredColumns = new HashSet<>();

        protected CsvReader reader;
        protected long row;
        // TODO "String column" that is set before any calls to avoid passing around the column name

        public Loader(GTFSFeed feed, String tableName) {
            this.feed = feed;
            this.tableName = tableName;
        }

        /**
         * @return whether the number actual is in the range [min, max]
         */
        protected boolean checkRangeInclusive(double min, double max, double actual) {
            if (actual < min || actual > max) {
                feed.errors.add(new RangeError(tableName, row, null, min, max, actual)); // TODO set column name in loader so it's available in methods
                return false;
            }
            return true;
        }

        /**
         * Fetch the value from the given column of the current row. Record an error the first time a column is
         * seen to be missing, and whenever empty values are encountered.
         * I was originally just calling getStringField from the other getXField functions as a first step to get
         * the missing-field check. But we don't want deduplication performed on strings that aren't being retained.
         * Therefore the missing-field behavior is this separate function.
         *
         * @return null if column was missing or field is empty
         */
        private String getFieldCheckRequired(String column, boolean required) throws IOException {
            String str = reader.get(column);
            if (str == null) {
                if (required && !missingRequiredColumns.contains(column)) {
                    feed.errors.add(new MissingColumnError(tableName, column));
                    missingRequiredColumns.add(column);
                }
            } else if (str.isEmpty()) {
                if (required) {
                    feed.errors.add(new EmptyFieldError(tableName, row, column));
                }
                str = null;
            }
            return str;
        }

        /**
         * @return the given column from the current row as a deduplicated String.
         */
        protected String getStringField(String column, boolean required) throws IOException {
            return getFieldCheckRequired(column, required);
        }

        protected int getIntField(String column, boolean required, int min, int max) throws IOException {
            return getIntField(column, required, min, max, 0);
        }


        protected int getIntField(String column, boolean required, int min, int max, int defaultValue) throws IOException {
            Map<Integer, Integer> mapping = null;
            return getIntField(column, required, min, max, defaultValue, mapping);
        }

        protected int getIntField(String column, boolean required, int min, int max, int defaultValue, final Map<Integer, Integer> mapping) throws IOException {
            String str = getFieldCheckRequired(column, required);
            int val = INT_MISSING;
            if (str == null) {
                val = defaultValue; // defaults to 0 per overloaded function, unless provided.
            } else {
                try {
                    val = Integer.parseInt(str);
                    if (mapping != null) {
                        Integer mappedVal = mapping.get(val);
                        if (mappedVal != null) {val = mappedVal;}
                    }
                    checkRangeInclusive(min, max, val);
                } catch (NumberFormatException nfe) {
                    feed.errors.add(new NumberParseError(tableName, row, column));
                }
            }
            return val;
        }

        /**
         * Fetch the given column of the current row, and interpret it as a time in the format HH:MM:SS.
         *
         * @return the time value in seconds since midnight
         */
        protected int getTimeField(String column, boolean required) throws IOException {
            String str = getFieldCheckRequired(column, required);
            int val = INT_MISSING;

            if (str != null) {
                String[] fields = str.split(":");
                if (fields.length != 3) {
                    feed.errors.add(new TimeParseError(tableName, row, column));
                } else {
                    try {
                        int hours = Integer.parseInt(fields[0]);
                        int minutes = Integer.parseInt(fields[1]);
                        int seconds = Integer.parseInt(fields[2]);
                        checkRangeInclusive(0, 72, hours); // GTFS hours can go past midnight. Some trains run for 3 days.
                        checkRangeInclusive(0, 59, minutes);
                        checkRangeInclusive(0, 59, seconds);
                        val = (hours * 60 * 60) + minutes * 60 + seconds;
                    } catch (NumberFormatException nfe) {
                        feed.errors.add(new TimeParseError(tableName, row, column));
                    }
                }
            }

            return val;
        }

        /**
         * Fetch the given column of the current row, and interpret it as a date in the format YYYYMMDD.
         *
         * @return the date value as Java LocalDate, or null if it could not be parsed.
         */
        protected LocalDate getDateField(String column, boolean required) throws IOException {
            String str = getFieldCheckRequired(column, required);
            LocalDate dateTime = null;
            if (str != null) {
                try {
                    dateTime = LocalDate.parse(str, DateTimeFormatter.BASIC_ISO_DATE);
                    checkRangeInclusive(2000, 2100, dateTime.getYear());
                } catch (IllegalArgumentException iae) {
                    feed.errors.add(new DateParseError(tableName, row, column));
                }
            }
            return dateTime;
        }

        /**
         * Fetch the given column of the current row, and interpret it as a URL.
         *
         * @return the URL, or null if the field was missing or empty.
         */
        protected URL getUrlField(String column, boolean required) throws IOException {
            String str = getFieldCheckRequired(column, required);
            URL url = null;
            if (str != null) {
                try {
                    url = new URL(str);
                } catch (MalformedURLException mue) {
                    feed.errors.add(new URLParseError(tableName, row, column));
                }
            }
            return url;
        }

        protected double getDoubleField(String column, boolean required, double min, double max) throws IOException {
            String str = getFieldCheckRequired(column, required);
            double val = Double.NaN;
            if (str != null) {
                try {
                    val = Double.parseDouble(str);
                    checkRangeInclusive(min, max, val);
                } catch (NumberFormatException nfe) {
                    feed.errors.add(new NumberParseError(tableName, row, column));
                }
            }
            return val;
        }

        /**
         * Used to check referential integrity.
         * Return value is not used, but could allow entities to point to each other directly rather than
         * using indirection through string-keyed maps.
         */
        protected <K, V> V getRefField(String column, boolean required, Map<K, V> target) throws IOException {
            String str = getFieldCheckRequired(column, required);
            V val = null;
            if (str != null) {
                val = target.get(str);
                if (val == null) {
                    feed.errors.add(new ReferentialIntegrityError(tableName, row, column, str));
                }
            }
            return val;
        }

        protected abstract boolean isRequired();

        /**
         * Implemented by subclasses to read one row, produce one GTFS entity, and store that entity in a map.
         */
        protected abstract void loadOneRow() throws IOException;

        /**
         * The main entry point into an Entity.Loader. Interprets each row of a CSV file within a zip file as a sinle
         * GTFS entity, and loads them into a table.
         *
         * @param zipOrDirectory the zip file or directory from which to read a table
         */
        public void loadTable(File zipOrDirectory) throws IOException {
            InputStream zis;
            if (zipOrDirectory.isDirectory()) {
                Path path = zipOrDirectory.toPath().resolve(tableName + ".txt");
                if (!path.toFile().exists()) {
                    missing();
                    return;
                }
                zis = new FileInputStream(path.toFile());
                LOG.info("Loading GTFS table {} from {}", tableName, path);
            } else {
                ZipFile zip = new ZipFile(zipOrDirectory);
                ZipEntry entry = zip.getEntry(tableName + ".txt");
                if (entry == null) {
                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    // check if table is contained within sub-directory
                    while (entries.hasMoreElements()) {
                        ZipEntry e = entries.nextElement();
                        if (e.getName().endsWith(tableName + ".txt")) {
                            entry = e;
                            feed.errors.add(new TableInSubdirectoryError(tableName, entry.getName().replace(tableName + ".txt", "")));
                        }
                    }
                    missing();
                    if (entry == null) return;
                }
                zis = zip.getInputStream(entry);
                LOG.info("Loading GTFS table {} from {}", tableName, entry);
            }
            // skip any byte order mark that may be present. Files must be UTF-8,
            // but the GTFS spec says that "files that include the UTF byte order mark are acceptable"
            InputStream bis = new BOMInputStream(zis);
            CsvReader reader = new CsvReader(bis, ',', Charset.forName("UTF8"));
            this.reader = reader;
            reader.readHeaders();
            while (reader.readRecord()) {
                // reader.getCurrentRecord() is zero-based and does not include the header line, keep our own row count
                if (++row % 500000 == 0) {
                    LOG.info("Record number {}", human(row));
                }
                loadOneRow(); // Call subclass method to produce an entity from the current row.
            }
        }

        private void missing() {
            if (this.isRequired()) {
                feed.errors.add(new MissingTableError(tableName));
            } else {
                LOG.info("Table {} was missing but it is not required.", tableName);
            }
        }

        private String human(long n) {
            if (n >= 1000000) return String.format(Locale.getDefault(), "%.1fM", n / 1000000.0);
            if (n >= 1000) {return String.format(Locale.getDefault(), "%.1fk", n / 1000.0);} else {
                return String.format(Locale.getDefault(), "%d", n);
            }
        }

        public static String convertToGtfsTime(int secsSinceMidnight) {
            int seconds = secsSinceMidnight % 60;
            secsSinceMidnight -= seconds;
            // note that the minute and hour values are still expressed in seconds until we write it out, to avoid unnecessary division.
            int minutes = (secsSinceMidnight % 3600);
            // secsSinceMidnight now represents hours
            secsSinceMidnight -= minutes;

            // integer divide is fine as we've subtracted off remainders
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", secsSinceMidnight / 3600, minutes / 60, seconds);
        }
    }

}
