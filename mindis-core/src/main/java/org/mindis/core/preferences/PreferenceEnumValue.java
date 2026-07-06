package org.mindis.core.preferences;

/**
 * A selectable preference value that can describe itself to the UI
 * Implementations are typically enums; a generic choice control renders
 * {@link #displayName()} and hides values whose{@link #isSelectable()}
 * is {@code false} (e.g. platform-dependent options).
 */
public interface PreferenceEnumValue {

    /**
     * Human-readable name of this value; localized where translation makes
     * sense (see {@code Theme}), deliberately fixed where it does not
     * (see {@code AppLanguage}: language names stay in their own language).
     */
    String displayName();

    default boolean isSelectable() {
        return true;
    }
}
