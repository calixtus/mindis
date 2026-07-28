package org.mindis.core.persistence.json;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import org.mindis.core.model.RecurrenceRule;

/// Jackson's view of the {@link RecurrenceRule} tree: a {@code kind} property
/// naming the rule, then the rule's own fields.
///
/// <p>Kept as a mixin instead of annotations on the model so that the record
/// hierarchy stays free of persistence concerns - the same reason the CSV form
/// lives in {@code RecurrenceCodec} rather than on the rules. It sits in its
/// own package because a class file carrying Jackson annotations makes every
/// downstream module that compiles against this package need the annotation
/// jar on its module path; nothing outside this module references
/// {@code org.mindis.core.persistence.json}.
///
/// <p>The names are part of the document format: rename one and existing
/// documents stop reading.
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RecurrenceRule.Weekday.class, name = "weekday"),
        @JsonSubTypes.Type(value = RecurrenceRule.DayOfMonth.class, name = "dayOfMonth"),
        @JsonSubTypes.Type(value = RecurrenceRule.NthWeekdayOfMonth.class, name = "nthWeekdayOfMonth"),
        @JsonSubTypes.Type(value = RecurrenceRule.MonthOfYear.class, name = "monthOfYear"),
        @JsonSubTypes.Type(value = RecurrenceRule.FixedMonthDay.class, name = "fixedMonthDay"),
        @JsonSubTypes.Type(value = RecurrenceRule.FixedDay.class, name = "fixedDay"),
        @JsonSubTypes.Type(value = RecurrenceRule.EveryNDays.class, name = "everyNDays"),
        @JsonSubTypes.Type(value = RecurrenceRule.EveryNWeeks.class, name = "everyNWeeks"),
        @JsonSubTypes.Type(value = RecurrenceRule.EveryNMonths.class, name = "everyNMonths"),
        @JsonSubTypes.Type(value = RecurrenceRule.FeastRelative.class, name = "feastRelative"),
        @JsonSubTypes.Type(value = RecurrenceRule.AllOf.class, name = "allOf"),
        @JsonSubTypes.Type(value = RecurrenceRule.AnyOf.class, name = "anyOf"),
        @JsonSubTypes.Type(value = RecurrenceRule.Not.class, name = "not"),
        @JsonSubTypes.Type(value = RecurrenceRule.Never.class, name = "never")})
public abstract class RecurrenceRuleMixin {
}
