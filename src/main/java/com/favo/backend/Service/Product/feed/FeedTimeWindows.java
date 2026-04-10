package com.favo.backend.Service.Product.feed;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Öne çıkan / trend hesapları için zaman pencereleri.
 * Takvim günleri ve hafta sınırları Europe/Istanbul ile belirlenir;
 * {@link #toUtcLocalDateTime(Instant)} ile DB'deki {@code LocalDateTime} (UTC kabulü) alanına çevrilir.
 */
public final class FeedTimeWindows {

    public static final ZoneId EUROPE_ISTANBUL = ZoneId.of("Europe/Istanbul");
    /** Sunucu/DB'de saklanan anların UTC olarak yorumlandığı varsayımı (Railway varsayılanı). */
    public static final ZoneId DB_AS_UTC = ZoneOffset.UTC;

    private FeedTimeWindows() {
    }

    /**
     * Son 7 takvim günü (İstanbul): bugün dahil, yerel tarih olarak (bugün - 6) .. bugün.
     * Aralık: [o gün 00:00 TR, yarın 00:00 TR) üzerinden üretilen anlar UTC'ye çevrilir.
     */
    public static TimeRangeInstant lastSevenCalendarDaysInclusiveTr() {
        ZonedDateTime nowTr = ZonedDateTime.now(EUROPE_ISTANBUL);
        LocalDate today = nowTr.toLocalDate();
        Instant start = today.minusDays(6).atStartOfDay(EUROPE_ISTANBUL).toInstant();
        Instant endExclusive = today.plusDays(1).atStartOfDay(EUROPE_ISTANBUL).toInstant();
        return new TimeRangeInstant(start, endExclusive);
    }

    /**
     * Takvim haftası (İstanbul): Pazartesi 00:00 – sonraki Pazartesi 00:00 (half-open).
     */
    public static TimeRangeInstant currentCalendarWeekTr() {
        ZonedDateTime nowTr = ZonedDateTime.now(EUROPE_ISTANBUL);
        LocalDate today = nowTr.toLocalDate();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant start = monday.atStartOfDay(EUROPE_ISTANBUL).toInstant();
        Instant endExclusive = monday.plusWeeks(1).atStartOfDay(EUROPE_ISTANBUL).toInstant();
        return new TimeRangeInstant(start, endExclusive);
    }

    public static LocalDateTime toUtcLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, DB_AS_UTC);
    }

    public record TimeRangeInstant(Instant startInclusive, Instant endExclusive) {
    }
}
