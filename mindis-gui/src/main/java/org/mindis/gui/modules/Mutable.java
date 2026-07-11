package org.mindis.gui.modules;

/// A single mutable slot for a value a lambda needs to capture and reassign
/// (e.g. re-deriving a live list on every editor callback, before the
/// callback that will use the new value exists yet - see {@code
/// ServicesModule#buildEditor}). Plain field, no atomicity/volatility
/// guarantees needed (this is single-threaded FX-thread code); exists purely
/// so a parameterized element type doesn't need a raw single-element array
/// (and the unchecked generic-array-creation warning that comes with one) -
/// non-generic holders (a single {@code boolean}, a {@code Runnable}) still
/// use that plain-array idiom directly, since they hit no such warning.
final class Mutable<T> {

    private T value;

    Mutable(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }

    void set(T value) {
        this.value = value;
    }
}
