/*
 * Copyright 2011-2024 Lime Mojito Pty Ltd
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package com.limemojito.trading.model.tick.dukascopy;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.time.LocalTime.MIDNIGHT;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.TemporalAdjusters.firstDayOfMonth;
import static java.time.temporal.TemporalAdjusters.firstDayOfYear;
import static java.time.temporal.TemporalAdjusters.lastDayOfMonth;
import static java.time.temporal.TemporalAdjusters.lastDayOfYear;
import static java.util.stream.Collectors.groupingBy;

/**
 * Generates canonical Dukascopy data file paths for a symbol over a given UTC time range.
 * <p>
 * Provides helpers to produce flat hourly paths or groups of hourly paths per day. The output is
 * suitable for consumption by caches and search components.
 * </p>
 * Paths follow the shape <code>EURUSD/2017/00/03/19h_ticks.bi5</code>
 */
@Component
@Slf4j
public class DukascopyPathGenerator {
    private final NumberFormat format00 = new DecimalFormat("00");

    /**
     * Generate Dukascopy hourly file paths grouped by day for the given symbol and time range.
     * <p>
     * Each inner list contains the 24 hourly paths (or a subset on boundary days) for a single day,
     * and the outer list is ordered by day.
     * </p>
     *
     * @param symbol           instrument symbol (e.g. EURUSD)
     * @param startInstantUtc  inclusive start instant (UTC)
     * @param endInstantUtc    inclusive end instant (UTC)
     * @return ordered groups of hourly paths, one list per day
     */
    public List<List<String>> generatePathsGroupedByDay(String symbol, Instant startInstantUtc, Instant endInstantUtc) {
        final List<String> paths = generatePaths(symbol, startInstantUtc, endInstantUtc);
        final int dayPathLength = 17;
        Map<String, List<String>> groupedByDay = paths.stream()
                                                      .collect(groupingBy(path -> path.substring(0, dayPathLength)));
        final List<List<String>> grouped = groupedByDay.keySet()
                                                       .stream()
                                                       .sorted()
                                                       .map(groupedByDay::get)
                                                       .collect(Collectors.toList());
        log.debug("Generated {} day groups", groupedByDay.size());
        return grouped;
    }

    /**
     * Generate the ordered list of Dukascopy hourly file paths for the given symbol and time range.
     *
     * @param symbol           instrument symbol (e.g. EURUSD)
     * @param startInstantUtc  inclusive start instant (UTC)
     * @param endInstantUtc    inclusive end instant (UTC)
     * @return ordered list of hourly file paths spanning the requested window
     * @throws IllegalArgumentException if {@code endInstantUtc} is not strictly after {@code startInstantUtc}
     */
    public List<String> generatePaths(String symbol, Instant startInstantUtc, Instant endInstantUtc) {
        final Criteria criteria = new Criteria(startInstantUtc, endInstantUtc, symbol);
        final LocalDateTime start = criteria.getStartUtc();
        final LocalDateTime end = criteria.getEndUtc();
        final List<String> paths = new LinkedList<>();
        if (start.getYear() == end.getYear()) {
            computePartialYear(paths, criteria, start, end);
        } else {
            computePartialYear(paths, criteria, start, start.with(lastDayOfYear()));
            if (end.getYear() - start.getYear() - 1 > 0) {
                for (int y = start.getYear() + 1; y <= end.getYear() - 1; y++) {
                    final LocalDateTime newStart = LocalDateTime.of(LocalDate.of(y, 1, 1), MIDNIGHT);
                    final LocalDateTime newEnd = newStart.plusYears(1).minusNanos(1);
                    computePartialYear(paths, criteria, newStart, newEnd);
                }
            }
            computePartialYear(paths, criteria, end.with(firstDayOfYear()), end);
        }
        log.debug("Generated {} paths", paths.size());
        return paths;
    }

    private void computePartialYear(List<String> paths,
                                    Criteria criteria,
                                    LocalDateTime startInYear,
                                    LocalDateTime endInYear) {
        if (startInYear.getMonthValue() == endInYear.getMonthValue()) {
            computePartialMonth(paths, criteria, startInYear, endInYear);
        } else {
            computePartialMonth(paths, criteria, startInYear, startInYear.with(lastDayOfMonth()));
            for (int m = startInYear.getMonthValue() + 1; m <= endInYear.getMonthValue() - 1; m++) {
                final LocalDate startDayOfMonth = LocalDate.of(startInYear.getYear(), m, 1);
                final LocalDateTime startOfMonth = LocalDateTime.of(startDayOfMonth, MIDNIGHT);
                final LocalDateTime endOfMonth = startOfMonth.with(lastDayOfMonth()).plusDays(1).minusNanos(1);
                computePartialMonth(paths, criteria, startOfMonth, endOfMonth);
            }
            computePartialMonth(paths, criteria, endInYear.with(firstDayOfMonth()), endInYear);
        }
    }

    private void computePartialMonth(List<String> paths,
                                     Criteria criteria,
                                     LocalDateTime startInMonth,
                                     LocalDateTime endInMonth) {
        for (int d = startInMonth.getDayOfMonth(); d <= endInMonth.getDayOfMonth(); d++) {
            computeDay(paths,
                       criteria,
                       LocalDate.of(startInMonth.getYear(),
                                    startInMonth.getMonthValue(),
                                    d));
        }
    }

    private void computeDay(List<String> paths, Criteria criteria, LocalDate atDay) {

        final int startHour = ifAtUseHourElse(atDay, criteria.getStartUtc(), 0);
        final int endHour = ifAtUseHourElse(atDay, criteria.getEndUtc(), 23);
        for (int h = startHour; h <= endHour; h++) {
            int year = atDay.getYear();
            int monthValue = atDay.getMonthValue();
            int dayOfMonth = atDay.getDayOfMonth();
            paths.add(dukascopyPath(criteria.getSymbol(), year, monthValue, dayOfMonth, h));
        }
    }

    private int ifAtUseHourElse(LocalDate atDay, LocalDateTime startUtc, int elseInt) {
        final LocalDate startDay = startUtc.toLocalDate();
        if (atDay.isEqual(startDay)) {
            return startUtc.getHour();
        }
        return elseInt;
    }

    private String dukascopyPath(String symbol, int year, int month, int day, int hour) {
        return format("%s/%d/%s/%s/%sh_ticks.bi5",
                      symbol,
                      year,
                      format00.format(month - 1),
                      format00.format(day),
                      format00.format(hour));
    }

    @Value
    @SuppressWarnings("RedundantModifiersValueLombok")
    private static final class Criteria {
        private Criteria(Instant start, Instant end, String symbol) {
            if (end.isBefore(start) || end.equals(start)) {
                throw new IllegalArgumentException(format("End %s must be after %s", end, start));
            }
            this.startUtc = LocalDateTime.ofInstant(start, UTC);
            this.endUtc = LocalDateTime.ofInstant(end, UTC);
            this.symbol = symbol;
        }

        private final LocalDateTime startUtc;
        private final LocalDateTime endUtc;
        private final String symbol;
    }
}
