/*
 * Copyright (c) 2007-2012, Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package javax.time.chrono;

import static javax.time.calendrical.ChronoUnit.SECONDS;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import javax.time.DateTimeException;
import javax.time.LocalDateTime;
import javax.time.ZoneId;
import javax.time.ZoneOffset;
import javax.time.calendrical.ChronoField;
import javax.time.calendrical.ChronoUnit;
import javax.time.calendrical.DateTime;
import javax.time.calendrical.DateTimeField;
import javax.time.calendrical.PeriodUnit;
import javax.time.jdk8.DefaultInterfaceChronoZonedDateTime;
import javax.time.zone.ZoneOffsetTransition;
import javax.time.zone.ZoneRules;

/**
 * A date-time with a time-zone in the calendar neutral API.
 * <p>
 * {@code ZoneChronoDateTime} is an immutable representation of a date-time with a time-zone.
 * This class stores all date and time fields, to a precision of nanoseconds,
 * as well as a time-zone and zone offset.
 * <p>
 * The purpose of storing the time-zone is to distinguish the ambiguous case where
 * the local time-line overlaps, typically as a result of the end of daylight time.
 * Information about the local-time can be obtained using methods on the time-zone.
 * <p>
 * This class provides control over what happens at these cutover points
 * (typically a gap in spring and an overlap in autumn). The {@link ZoneResolver}
 * interface and implementations in {@link ZoneResolvers} provide strategies for
 * handling these cases. The methods {@link #withEarlierOffsetAtOverlap()} and
 * {@link #withLaterOffsetAtOverlap()} provide further control for overlaps.
 *
 * <h4>Implementation notes</h4>
 * This class is immutable and thread-safe.
 *
 * @param <C> the chronology of this date
 */
final class ChronoZonedDateTimeImpl<C extends Chrono<C>>
        extends DefaultInterfaceChronoZonedDateTime<C>
        implements ChronoZonedDateTime<C>, Serializable {

    /**
     * Serialization version.
     */
    private static final long serialVersionUID = -5261813987200935591L;

    /**
     * The local date-time.
     */
    private final ChronoDateTimeImpl<C> dateTime;
    /**
     * The zone offset.
     */
    private final ZoneOffset offset;
    /**
     * The zone ID.
     */
    private final ZoneId zoneId;

    //-----------------------------------------------------------------------
    /**
     * Obtains an instance of {@code ZoneChronoDateTime} from a local date-time
     * providing a resolver to handle an invalid date-time.
     * <p>
     * This factory creates a {@code ZoneChronoDateTime} from a date-time and time-zone.
     * If the time is invalid for the zone, due to either being a gap or an overlap,
     * then the resolver will determine what action to take.
     * See {@link ZoneResolvers} for common resolver implementations.
     *
     * @param dateTime  the local date-time, not null
     * @param zoneId  the time-zone, not null
     * @return the zoned date-time, not null
     * @throws DateTimeException if the resolver cannot resolve an invalid local date-time
     */
    static <R extends Chrono<R>> ChronoZonedDateTime<R> of(ChronoDateTimeImpl<R> dateTime, ZoneId zoneId) {
        return resolve(dateTime, zoneId, null, resolver);
    }

    /**
     * Obtains an instance of {@code ZoneChronoDateTime} from an {@code ChronoOffsetDateTime}
     * ensuring that the offset provided is valid for the time-zone.
     * <p>
     * This factory creates a {@code ZoneChronoDateTime} from an offset date-time and time-zone.
     * If the date-time is invalid for the zone due to a time-line gap then an exception is thrown.
     * Otherwise, the offset is checked against the zone to ensure it is valid.
     * <p>
     * An alternative to this method is {@link #ofInstant}. This method will retain
     * the date and time and throw an exception if the offset is invalid.
     * The {@code ofInstant} method will change the date and time if necessary
     * to retain the same instant.
     *
     * @param dateTime  the offset date-time to use, not null
     * @param zoneId  the time-zone, not null
     * @return the zoned date-time, not null
     * @throws DateTimeException if no rules can be found for the zone
     * @throws DateTimeException if the date-time is invalid due to a gap in the local time-line
     * @throws DateTimeException if the offset is invalid for the time-zone at the date-time
     */
    static <R extends Chrono<R>> ChronoZonedDateTimeImpl<R> ofStrict(ChronoDateTimeImpl<R> dateTime, ZoneId zoneId) {
        Objects.requireNonNull(dateTime, "dateTime");
        Objects.requireNonNull(zoneId, "zoneId");
        LocalDateTime inputLDT = LocalDateTime.from(dateTime.getDateTime());
        ZoneOffset inputOffset = dateTime.getOffset();
        ZoneRules rules = zoneId.getRules();  // latest rules version
        List<ZoneOffset> validOffsets = rules.getValidOffsets(inputLDT);
        if (validOffsets.contains(inputOffset) == false) {
            if (validOffsets.size() == 0) {
                throw new DateTimeException("The local time " + inputLDT +
                        " does not exist in time-zone " + zoneId + " due to a daylight savings gap");
            }
            throw new DateTimeException("The offset in the date-time " + dateTime +
                    " is invalid for time-zone " + zoneId);
        }
        return new ChronoZonedDateTimeImpl<>(dateTime, zoneId);
    }

//    /**
//     * Obtains an instance of {@code ZoneChronoDateTime} from an {@code ChronoOffsetDateTime}.
//     * <p>
//     * The resulting date-time represents exactly the same instant on the time-line.
//     * As such, the resulting local date-time may be different from the input.
//     * <p>
//     * If the instant represents a point on the time-line outside the supported year
//     * range then an exception will be thrown.
//     *
//     * @param instantDateTime  the instant to create the date-time from, not null
//     * @param zoneId  the time-zone to use, not null
//     * @return the zoned date-time, not null
//     * @throws DateTimeException if the result exceeds the supported range
//     */
//    private static <R extends Chrono<R>> ChronoZonedDateTimeImpl<R> ofInstant(
//                ChronoOffsetDateTimeImpl<R> instantDateTime, ZoneId zoneId) {
//        Objects.requireNonNull(instantDateTime, "instantDateTime");
//        Objects.requireNonNull(zoneId, "zoneId");
//        ZoneRules rules = zoneId.getRules();  // latest rules version
//        // Add optimization to avoid toInstant
//        instantDateTime = instantDateTime.withOffsetSameInstant(rules.getOffset(instantDateTime.toInstant()));
//        instantDateTime.atZoneSameInstant(zoneId); ///recurse
//        return new ChronoZonedDateTimeImpl<R>(instantDateTime, zoneId);
//    }
//
//    //-----------------------------------------------------------------------
//    /**
//     * Obtains an instance of {@code ZoneChronoDateTime}.
//     *
//     * @param desiredLocalDateTime  the date-time, not null
//     * @param zoneId  the time-zone, not null
//     * @param oldDateTime  the old date-time prior to the calculation, may be null
//     * @param resolver  the resolver from local date-time to zoned, not null
//     * @return the zoned date-time, not null
//     * @throws DateTimeException if the date-time cannot be resolved
//     */
//    private static <R extends Chrono<R>> ChronoZonedDateTime<R>
//            resolve(ChronoLocalDateTime<R> desiredLocalDateTime, ZoneId zoneId,
//                    ChronoOffsetDateTime<?> oldDateTime, ZoneResolver resolver) {
//        Objects.requireNonNull(desiredLocalDateTime, "desiredLocalDateTime");
//        Objects.requireNonNull(zoneId, "zoneId");
//        Objects.requireNonNull(resolver, "resolver");
//        ZoneRules rules = zoneId.getRules();
//        LocalDateTime desired = LocalDateTime.from(desiredLocalDateTime);
//        OffsetDateTime old = (oldDateTime == null ? null : OffsetDateTime.from(oldDateTime));
//        List<ZoneOffset> validOffsets = rules.getValidOffsets(desired);
//        OffsetDateTime offsetDT;
//        if (validOffsets.size() == 1) {
//            offsetDT = OffsetDateTime.of(desired, validOffsets.get(0));
//        } else {
//            ZoneOffsetTransition trans = rules.getTransition(desired);
//            offsetDT = resolver.resolve(desired, trans, rules, zoneId, old);
//            if (((offsetDT.getDateTime() == desired && validOffsets.contains(offsetDT.getOffset())) ||
//                    rules.isValidOffset(offsetDT.getDateTime(), offsetDT.getOffset())) == false) {
//                throw new DateTimeException(
//                    "ZoneResolver implementation must return a valid date-time and offset for the zone: " + resolver.getClass().getName());
//            }
//        }
//        // Convert the date back to the current chronology and set the time.
//        ChronoLocalDateTime<R> cdt = desiredLocalDateTime.with(EPOCH_DAY, offsetDT.getDate().toEpochDay()).with(offsetDT.getTime());
//        ChronoOffsetDateTime<R> codt = cdt.atOffset(offsetDT.getOffset());
//        return codt.atZoneSimilarLocal(zoneId);
//    }

    //-----------------------------------------------------------------------
    /**
     * Constructor.
     *
     * @param dateTime  the date-time, not null
     * @param offset  the zone offset, not null
     * @param zone  the zone ID, not null
     */
    private ChronoZonedDateTimeImpl(ChronoDateTimeImpl<C> dateTime, ZoneOffset offset, ZoneId zoneId) {
        Objects.requireNonNull(dateTime, "dateTime");
        Objects.requireNonNull(offset, "offset");
        Objects.requireNonNull(zoneId, "zoneId");
        this.dateTime = dateTime;
        this.offset = offset;
        this.zoneId = zoneId;
    }

    private ChronoZonedDateTime<C> resolveLocal(ChronoLocalDateTime<C> newDateTime) {
        return ofBest(newDateTime, zoneId, offset);
    }

    private ChronoZonedDateTime<C> resolveOffset(ZoneOffset offset) {
        long epSec = dateTime.toEpochSecond(offset);
        return create(epSec, dateTime.getTime().getNano(), zoneId);
    }

    //-----------------------------------------------------------------------
    public ZoneOffset getOffset() {
        return offset;
    }

    @Override
    public ChronoZonedDateTime<C> withEarlierOffsetAtOverlap() {
        ZoneOffsetTransition trans = getZone().getRules().getTransition(LocalDateTime.from(this));
        if (trans != null && trans.isOverlap()) {
            ZoneOffset earlierOffset = trans.getOffsetBefore();
            if (earlierOffset.equals(offset) == false) {
                return new ChronoZonedDateTimeImpl<C>(dateTime, earlierOffset, zoneId);
            }
        }
        return this;
    }

    @Override
    public ChronoZonedDateTime<C> withLaterOffsetAtOverlap() {
        ZoneOffsetTransition trans = getZone().getRules().getTransition(LocalDateTime.from(this));
        if (trans != null) {
            ZoneOffset offset = trans.getOffsetAfter();
            if (offset.equals(getOffset()) == false) {
                return new ChronoZonedDateTimeImpl<C>(dateTime, offset, zoneId);
            }
        }
        return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public ChronoLocalDateTime<C> getDateTime() {
        return dateTime;
    }

    public ZoneId getZone() {
        return zoneId;
    }

    public ChronoZonedDateTime<C> withZoneSameLocal(ZoneId zoneId) {
        return ofBest(dateTime, zoneId, offset);
    }

    @Override
    public ChronoZonedDateTime<C> withZoneSameInstant(ZoneId zoneId) {
        Objects.requireNonNull(zoneId, "zoneId");
        return this.zoneId.equals(zoneId) ? this :
            create(dateTime.toEpochSecond(offset), dateTime.getTime().getNano(), zoneId);
    }

    //-----------------------------------------------------------------------
    @Override
    public boolean isSupported(DateTimeField field) {
        return field instanceof ChronoField || (field != null && field.doIsSupported(this));
    }

    //-----------------------------------------------------------------------
    @Override
    public ChronoZonedDateTime<C> with(DateTimeField field, long newValue) {
        if (field instanceof ChronoField) {
            ChronoField f = (ChronoField) field;
            switch (f) {
                case INSTANT_SECONDS: return plus(newValue - toEpochSecond(), SECONDS);
                case OFFSET_SECONDS: {
                    ZoneOffset offset = ZoneOffset.ofTotalSeconds(f.checkValidIntValue(newValue));
                    return resolveOffset(offset);
                }
            }
            return resolveLocal(dateTime.with(field, newValue));
        }
        return getDate().getChrono().ensureChronoZonedDateTime(field.doWith(this, newValue));
    }

    //-----------------------------------------------------------------------
    @Override
    public ChronoZonedDateTime<C> plus(long amountToAdd, PeriodUnit unit) {
        if (unit instanceof ChronoUnit) {
            return with(dateTime.plus(amountToAdd, unit));
        }
        return getDate().getChrono().ensureChronoZonedDateTime(unit.doPlus(this, amountToAdd));   /// TODO: Generics replacement Risk!
    }

    //-----------------------------------------------------------------------
    @Override
    public long periodUntil(DateTime endDateTime, PeriodUnit unit) {
        if (endDateTime instanceof ChronoZonedDateTime == false) {
            throw new DateTimeException("Unable to calculate period between objects of two different types");
        }
        @SuppressWarnings("unchecked")
        ChronoZonedDateTime<C> end = (ChronoZonedDateTime<C>) endDateTime;
        if (getDate().getChrono().equals(end.getDate().getChrono()) == false) {
            throw new DateTimeException("Unable to calculate period between two different chronologies");
        }
        if (unit instanceof ChronoUnit) {
            end = end.withZoneSameInstant(offset);
            return dateTime.periodUntil(end.getDateTime(), unit);
        }
        return unit.between(this, endDateTime).getAmount();
    }

    //-----------------------------------------------------------------------
    private Object writeReplace() {
        return new Ser(Ser.CHRONO_ZONEDDATETIME_TYPE, this);
    }

    void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(dateTime);
        out.writeObject(zoneId);
    }

    static ChronoZonedDateTime<?> readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        ChronoLocalDateTime<?> dateTime = (ChronoLocalDateTime<?>) in.readObject();
        ZoneOffset offset = (ZoneOffset) in.readObject();
        ZoneId zone = (ZoneId) in.readObject();
        return dateTime.atZone(offset).withZoneSameLocal(zone);
        // TODO: ZDT uses ofLenient()
    }

}
