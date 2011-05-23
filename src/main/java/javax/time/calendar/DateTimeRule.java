/*
 * Copyright (c) 2007-2011 Stephen Colebourne & Michael Nascimento Santos
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
package javax.time.calendar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

import javax.time.CalendricalException;
import javax.time.calendar.format.DateTimeFormatterBuilder.TextStyle;

/**
 * The rule defining how a measurable field of time operates.
 * <p>
 * Rule implementations define how a field like day-of-month operates.
 * This includes the field name and minimum/maximum values.
 * <p>
 * This class is abstract and must be implemented with care to
 * ensure other classes in the framework operate correctly.
 * All instantiable subclasses must be final, immutable and thread-safe and must
 * ensure serialization works correctly.
 *
 * @author Michael Nascimento Santos
 * @author Stephen Colebourne
 */
public abstract class DateTimeRule extends CalendricalRule<DateTimeField> {
    // TODO: broken serialization

    /** A serialization identifier for this class. */
    private static final long serialVersionUID = 1L;

    /** The outer range of values for the rule. */
    private final DateTimeRuleRange range;
    /** The base rule that this rule relates to. */
    private final DateTimeRule baseRule;

    /**
     * Creates an instance specifying the minimum and maximum value of the rule.
     *
     * @param chronology  the chronology, not null
     * @param name  the name of the type, not null
     * @param periodUnit  the period unit, not null
     * @param periodRange  the period range, not null
     * @param minimumValue  the minimum value
     * @param maximumValue  the minimum value
     */
    protected DateTimeRule(
            Chronology chronology,
            String name,
            PeriodUnit periodUnit,
            PeriodUnit periodRange,
            long minimumValue,
            long maximumValue) {
        this(chronology, name, periodUnit, periodRange, DateTimeRuleRange.of(minimumValue, maximumValue));
    }

    /**
     * Creates an instance specifying the outer range of value for the rule.
     *
     * @param chronology  the chronology, not null
     * @param name  the name of the type, not null
     * @param periodUnit  the period unit, not null
     * @param periodRange  the period range, not null
     * @param range  the range, not null
     */
    protected DateTimeRule(
            Chronology chronology,
            String name,
            PeriodUnit periodUnit,
            PeriodUnit periodRange,
            DateTimeRuleRange range) {
        this(chronology, name, periodUnit, periodRange, range, null);
    }

    /**
     * Creates an instance specifying the outer range of value for the rule
     * and the rule that this is related to.
     *
     * @param chronology  the chronology, not null
     * @param name  the name of the type, not null
     * @param periodUnit  the period unit, not null
     * @param periodRange  the period range, not null
     * @param range  the range, not null
     * @param baseRule  the base rule that this rule relates to, null
     *  if this rule does not relate to another rule
     */
    protected DateTimeRule(
            Chronology chronology,
            String name,
            PeriodUnit periodUnit,
            PeriodUnit periodRange,
            DateTimeRuleRange range,
            DateTimeRule baseRule) {
        super(DateTimeField.class, chronology, name, periodUnit, periodRange);
        ISOChronology.checkNotNull(range, "DateTimeRuleRange must not be null");
        this.range = range;
        this.baseRule = (baseRule != null ? baseRule : this);
        if (baseRule != null) {
            DateTimeRuleGroup.of(baseRule).registerRelatedRule0(this);
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the valid range of values for this rule.
     * <p>
     * For example, the 'DayOfMonth' rule has values from 1 to between 28 and 31.
     *
     * @return the valid range of values, not null
     */
    public DateTimeRuleRange getRange() {
        return range;
    }

    /**
     * Gets the valid range of values for this rule using the specified
     * calendrical to refine the accuracy of the response.
     * <p>
     * This uses the calendrical to return a more accurate range of valid values.
     * The result of this method may still be inaccurate, if there is insufficient
     * information in the calendrical.
     * For example, the 'DayOfMonth' rule has values from 1 to between 28 and 31.
     * If the calendrical specifies 'February', then the returned range will be from
     * 1 to between 28 and 29. If the calendrical specifies 'February' in a leap year,
     * then the returned range will be from 1 to 29 exactly.
     * <p>
     * The default implementation returns {@link #getRange()}.
     * Subclasses must override this as necessary.
     *
     * @param calendrical  context calendrical, not null
     * @return the valid range of values given the calendrical context, not null
     */
    public DateTimeRuleRange getRange(Calendrical calendrical) {
        ISOChronology.checkNotNull(calendrical, "Calendrical must not be null");
        return getRange();
    }

    //-----------------------------------------------------------------------
    /**
     * Checks if the rule defines values that fit in an {@code int}, throwing an exception if not.
     * <p>
     * This checks that all valid values are within the bounds of an {@code int}.
     * <p>
     * For example, the 'MonthOfYear' rule has values from 1 to 12, which fits in an {@code int}.
     * By comparison, 'NanoOfDay' runs from 1 to 86,400,000,000,000 which does not fit in an {@code int}.
     * <p>
     * This implementation uses {@link DateTimeRuleRange#isIntValue()}.
     * Subclasses should not normally override this method.
     *
     * @return true if a valid value always fits in an {@code int}
     * @throws CalendricalException if the value does not fit in an {@code int}
     */
    public void checkIntValue() {
        DateTimeRuleRange range = getRange();
        if (range.isIntValue() == false) {
            throw new CalendricalRuleException("Rule does not specify an int value: " + getName(), this);
        }
    }

    /**
     * Checks if the value is valid for the rule, throwing an exception if invalid.
     * <p>
     * This checks that the value is within the valid range of the rule.
     * This method considers the rule in isolation, thus only the
     * outer minimum and maximum range for the field is validated.
     * For example, 'DayOfMonth' has the outer value-range of 1 to 31.
     * <p>
     * This implementation uses {@link DateTimeRuleRange#isValidValue(long)}.
     * Subclasses should not normally override this method..
     *
     * @param value  the value to check
     * @return the valid value
     * @throws IllegalCalendarFieldValueException if the value is invalid
     */
    public long checkValidValue(long value) {
        DateTimeRuleRange range = getRange();
        if (range.isValidValue(value) == false) {
            throw new IllegalCalendarFieldValueException(this, value);
        }
        return value;
    }

    /**
     * Checks if the value is valid for the rule and that the rule defines
     * values that fit in an {@code int}, throwing an exception if not.
     * <p>
     * This checks that the value is within the valid range of the rule and
     * that all valid values are within the bounds of an {@code int}.
     * This method considers the rule in isolation, thus only the
     * outer minimum and maximum range for the field is validated.
     * For example, 'DayOfMonth' has the outer value-range of 1 to 31.
     * <p>
     * This implementation uses {@link #checkIntValue()} and {@link #checkValidValue(long)}.
     * Subclasses should not normally override this method.
     *
     * @param value  the value to check
     * @return the valid value as an {@code int}
     * @throws CalendricalException if the value does not fit in an {@code int}
     * @throws IllegalCalendarFieldValueException if the value is invalid
     */
    public int checkValidIntValue(long value) {
        checkIntValue();
        return (int) checkValidValue(value);
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the textual representation of a value in this rule.
     * <p>
     * This returns the textual representation of the field, such as for day-of-week or month-of-year.
     * If no textual mapping is found then the {@link #getValue() numeric value} is returned.
     * <p>
     * This implementation uses {@link #field(long)} and {@link DateTimeField#getText(TextStyle, Locale)}.
     * Subclasses should not normally override this method.
     *
     * @param value  the value to convert to text, must be valid for the rule
     * @param textStyle  the text style, not null
     * @param locale  the locale to use, not null
     * @return the textual representation of the field, not null
     */
    public String getText(long value, TextStyle textStyle, Locale locale) {
        return field(value).getText(textStyle, locale);
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the base rule that this rule is related to.
     * <p>
     * Each rule typically has a connection to another rule.
     * For example, the 'SecondOfMinute' and 'MinuteOfHour' rules are related
     * and can be combined. The base rule is the rule that encompasses a group
     * of related rules. For example, 'NanoOfDay' is the rule that encompasses
     * all the major time rules.
     *
     * @return the base rule, not null
     */
    public DateTimeRule getBaseRule() {
        return baseRule;
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the equivalent period for a value in this rule.
     * <p>
     * The period is the period that the value is after the start of the range.
     * This essentially converts the value to a simple sequential zero-based value.
     * The method will handle out of range values wherever possible.
     * <p>
     * For example, consider a day-of-year running from 1 to 365/366.
     * The equivalent period will run from 0 to 364/365, as day 1 requires adding
     * zero days to the start of the year.
     * <p>
     * This implementation simply returns the value as the period, which is suitable
     * for any sequential zero-based field, such as minute-of-hour.
     * Subclasses must override this as necessary.
     *
     * @param value  the value of this rule, may be outside the value range for the rule
     * @return the period equivalent to the value of this rule in units of this rule, not null
     * @throws CalendricalException if a suitable conversion is not possible
     */
    public long convertToPeriod(long value) {
        return value;
    }

    /**
     * Gets the equivalent value for a period measured in units of this rule.
     * <p>
     * The period is the period that the value is after the start of the range.
     * This essentially converts the value from a simple sequential zero-based
     * amount to the potentially complex value.
     * The method will handle out of range values wherever possible.
     * <p>
     * For example, consider a day-of-year running from 1 to 365/366.
     * The equivalent period will run from 0 to 364/365, as day 1 requires adding
     * zero days to the start of the year.
     * <p>
     * This implementation simply returns the period as the value, which is suitable
     * for any sequential zero-based field, such as minute-of-hour.
     * Subclasses must override this as necessary.
     *
     * @param period  the period measured in units of this rule, positive or negative
     * @return the value of this rule, potentially out of range, not null
     * @throws CalendricalException if a suitable conversion is not possible
     */
    public long convertFromPeriod(long period) {
        return period;
    }

    //-----------------------------------------------------------------------
    /**
     * Converts a value for this field to a fraction between 0 and 1.
     * <p>
     * The fractional value is between 0 (inclusive) and 1 (exclusive).
     * It can only be returned if the {@link #getRange() value range} is fixed.
     * The fraction is obtained by calculation from the field range using 9 decimal
     * places and a rounding mode of {@link RoundingMode#FLOOR FLOOR}.
     * The calculation is inaccurate if the values do not run continuously from smallest to largest.
     * <p>
     * For example, the second-of-minute value of 15 would be returned as 0.25,
     * assuming the standard definition of 60 seconds in a minute.
     * <p>
     * Subclasses should not normally override this method.
     *
     * @param value  the value to convert, must be valid for this rule
     * @return the value as a fraction within the range, from 0 to 1, not null
     * @throws CalendricalRuleException if the value cannot be converted to a fraction
     */
    public BigDecimal convertToFraction(long value) {
        DateTimeRuleRange range = getRange();
        if (range.isFixed() == false) {
            throw new CalendricalRuleException("The fractional value of " + getName() +
                    " cannot be obtained as the range is not fixed", this);
        }
        checkValidValue(value);
        BigDecimal minBD = BigDecimal.valueOf(range.getMinimumValue());
        BigDecimal rangeBD = BigDecimal.valueOf(range.getMaximumValue()).subtract(minBD).add(BigDecimal.ONE);
        BigDecimal valueBD = BigDecimal.valueOf(value).subtract(minBD);
        BigDecimal fraction = valueBD.divide(rangeBD, 9, RoundingMode.FLOOR);
        // stripTrailingZeros bug
        return fraction.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : fraction.stripTrailingZeros();
    }

    /**
     * Converts a fraction from 0 to 1 for this field to a value.
     * <p>
     * The fractional value must be between 0 (inclusive) and 1 (exclusive).
     * It can only be returned if the {@link #getRange() value range} is fixed.
     * The value is obtained by calculation from the field range and a rounding
     * mode of {@link RoundingMode#FLOOR FLOOR}.
     * The calculation is inaccurate if the values do not run continuously from smallest to largest.
     * <p>
     * For example, the fractional second-of-minute of 0.25 would be converted to 15,
     * assuming the standard definition of 60 seconds in a minute.
     * <p>
     * Subclasses should not normally override this method.
     *
     * @param fraction  the fraction to convert, not null
     * @return the value of the field, valid for this rule
     * @throws UnsupportedRuleException if the value cannot be converted
     * @throws IllegalCalendarFieldValueException if the value is invalid
     */
    public long convertFromFraction(BigDecimal fraction) {
        DateTimeRuleRange range = getRange();
        if (range.isFixed() == false) {
            throw new UnsupportedRuleException("The fractional value of " + getName() +
                    " cannot be converted as the range is not fixed", this);
        }
        BigDecimal minBD = BigDecimal.valueOf(range.getMinimumValue());
        BigDecimal rangeBD = BigDecimal.valueOf(range.getMaximumValue()).subtract(minBD).add(BigDecimal.ONE);
        BigDecimal valueBD = fraction.multiply(rangeBD).setScale(0, RoundingMode.FLOOR).add(minBD);
        long value = valueBD.longValueExact();
        checkValidValue(value);
        return value;
    }

    //-----------------------------------------------------------------------
    /**
     * Creates a field for this rule.
     * <p>
     * Subclasses should not normally override this method.
     * 
     * @param value  the value to create the field for, may be outside the valid range for the rule
     * @return the created field, not null
     */
    public DateTimeField field(long value) {
       return DateTimeField.of(this, value); 
    }

}
