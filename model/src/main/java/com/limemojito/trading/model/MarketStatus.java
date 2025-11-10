/*
 * Copyright 2011-2025 Lime Mojito Pty Ltd
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

package com.limemojito.trading.model;

import lombok.extern.slf4j.Slf4j;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;

/**
 * Functions for checking if the market is open or closed.
 */
@Slf4j
public class MarketStatus {

    private final ZoneId newYorkZone;
    private final ZoneId sydneyZone;

    public MarketStatus() {
        newYorkZone = ZoneId.of("America/New_York");
        sydneyZone = ZoneId.of("Australia/Sydney");
    }

    /**
     * Market states
     */
    public enum Status {
        OPEN, CLOSED
    }

    /**
     * Checks if the FX market is open at the given instant.  Open is defined as the instant being after or equal to the
     * Sydney session start and less than or equal to the New York session close.  This calculation takes into account
     * session daylight savings times, etc.
     *
     * @param instant The UTC instant to check
     * @return OPEN if the market is open at the given instant, CLOSED otherwise.
     */
    public Status isOpen(Instant instant) {
        Status status = isAfterSydneyWeekStart(instant) && isBeforeNewYorkWeekEndFor(instant)
                ? Status.OPEN
                : Status.CLOSED;
        log.trace("Market status is {} for {}", status, instant);
        return status;
    }

    private boolean isBeforeNewYorkWeekEndFor(Instant instant) {
        ZonedDateTime newYorkTime = instant.atZone(newYorkZone);
        log.trace("Query {} is {} {} at NewYork", instant, newYorkTime.getDayOfWeek(), newYorkTime);
        final int newYorkCloseHour = 17;
        // Sunday may be a trading day as Sydney Monday 9am overlaps.
        if (newYorkTime.getDayOfWeek() == SATURDAY) {
            log.trace("Saturday in NY is never a trading day");
            return false;
        }
        if (newYorkTime.getDayOfWeek() == DayOfWeek.FRIDAY) {
            boolean beforeClose = newYorkTime.getHour() < newYorkCloseHour;
            log.trace("Friday before close at {}? {}", newYorkCloseHour, beforeClose);
            return beforeClose;
        }
        Instant newYorkWeekEnd = newYorkTime.with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
                                            .withHour(newYorkCloseHour).withMinute(0).withSecond(0).withNano(0)
                                            .toInstant();
        log.trace("New York session end is {}", newYorkWeekEnd);
        return newYorkWeekEnd.isAfter(instant);
    }

    private boolean isAfterSydneyWeekStart(Instant instant) {
        ZonedDateTime atSydney = instant.atZone(sydneyZone);
        log.trace("Query {} is {} {} at Sydney", instant, atSydney.getDayOfWeek(), atSydney);
        // Saturday may be a trading day as NY Friday 4pm overlaps.
        if (atSydney.getDayOfWeek() == SUNDAY) {
            log.trace("Sunday in Sydney is never a trading day");
            return false;
        }
        final int sydneyOpenHour = 9;
        if (atSydney.getDayOfWeek() == MONDAY) {
            boolean afterOpen = atSydney.getHour() >= sydneyOpenHour;
            log.trace("Monday after open at {}AM? {}", sydneyOpenHour, afterOpen);
            return afterOpen;
        }
        Instant sydneySessionStart = atSydney.with(TemporalAdjusters.previous(MONDAY))
                                             .withHour(sydneyOpenHour).withMinute(0).withSecond(0).withNano(0)
                                             .toInstant();
        log.trace("Sydney session start is {}", sydneySessionStart);
        return sydneySessionStart.isBefore(instant);
    }

}
