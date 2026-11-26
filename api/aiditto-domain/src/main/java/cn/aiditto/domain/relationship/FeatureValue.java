package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.RelationshipTypes.ValueType;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * A stated value. Absence of a feature is UNKNOWN and is not represented here.
 * {@link Unbounded} is 不限. {@link Withheld} is 不愿透露. Neither is a midpoint.
 */
public sealed interface FeatureValue permits FeatureValue.Bool, FeatureValue.EnumOne, FeatureValue.EnumMany,
        FeatureValue.Num, FeatureValue.Span, FeatureValue.Text, FeatureValue.Unbounded, FeatureValue.Withheld,
        FeatureValue.ControlledRef {

    ValueType type();

    record Bool(boolean value) implements FeatureValue {
        public ValueType type() { return ValueType.BOOLEAN; }
    }

    record EnumOne(String value) implements FeatureValue {
        public EnumOne {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("enum value");
        }
        public ValueType type() { return ValueType.ENUM; }
    }

    record EnumMany(List<String> values) implements FeatureValue {
        public EnumMany {
            TreeSet<String> sorted = new TreeSet<>();
            for (String v : values) {
                if (v == null || v.isBlank()) throw new IllegalArgumentException("enum set value");
                sorted.add(v);
            }
            if (sorted.isEmpty()) throw new IllegalArgumentException("enum set is empty");
            values = List.copyOf(sorted);
        }
        public ValueType type() { return ValueType.ENUM_SET; }
    }

    record Num(double value) implements FeatureValue {
        public Num {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("number must be finite");
        }
        public ValueType type() { return ValueType.NUMBER; }
    }

    record Span(double min, double max) implements FeatureValue {
        public Span {
            if (!Double.isFinite(min) || !Double.isFinite(max)) throw new IllegalArgumentException("range must be finite");
            if (min > max) throw new IllegalArgumentException("range min above max");
        }
        public ValueType type() { return ValueType.RANGE; }
    }

    record Text(String value) implements FeatureValue {
        public Text {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("text value");
            value = value.strip();
        }
        public ValueType type() { return ValueType.TEXT; }
    }

    record Unbounded() implements FeatureValue {
        public ValueType type() { return ValueType.ENUM; }
    }

    record Withheld() implements FeatureValue {
        public ValueType type() { return ValueType.ENUM; }
    }

    record ControlledRef(String ref) implements FeatureValue {
        public ControlledRef {
            if (ref == null || ref.isBlank() || ref.indexOf(' ') >= 0) throw new IllegalArgumentException("controlled ref");
        }
        public ValueType type() { return ValueType.TEXT; }
    }

    static FeatureValue enumMany(String... values) {
        return new EnumMany(List.of(values));
    }

    static List<String> enumList(FeatureValue value) {
        if (value instanceof EnumMany many) return many.values();
        if (value instanceof EnumOne one) return List.of(one.value());
        return List.of();
    }

    static boolean same(FeatureValue a, FeatureValue b) {
        if (a == null || b == null) return a == b;
        return a.equals(b);
    }

    static List<String> sortedCopy(List<String> values) {
        return new ArrayList<>(new TreeSet<>(values));
    }
}
