package org.mindis.workbench;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.jspecify.annotations.Nullable;

/**
 * Shared JavaFX-observable mirror over one repository's staged in-memory
 * state, plus GUI-side dirty bookkeeping. One instance per entity type for
 * the whole application lifetime (constructed once at startup, surviving UI
 * rebuilds), so unsaved edits made in one module are immediately visible in
 * every other module bound to the same store.
 *
 * <p><b>Write-through</b>: every mutation ({@link #updateLive}, {@link
 * #insertFirst}, {@link #remove}, {@link #mergeLive}) updates {@link #items()}
 * <em>and</em> stages the change into the repository cache in the same call -
 * the repository stays the single source of truth for non-GUI readers (the
 * solver, CSV mappers, generators). None of these touch disk; flushing and
 * reloading happen at the repository level (global Save all/Load), after
 * which {@link #refresh()} re-mirrors and re-baselines this store.
 *
 * <p><b>Dirty tracking</b> (same algorithm as the former per-module
 * bookkeeping): a row is dirty when it differs from its snapshot in the
 * last-<em>flushed</em> baseline (per the {@code equivalence} predicate) or
 * has no snapshot yet (a new row); each removal of a previously-flushed row
 * also counts until the next flush. The baseline moves only in
 * {@link #refresh()}.
 *
 * @param <T> the item type; must have a stable identity via the
 *            {@code identity} function (e.g. a record's {@code id()})
 */
public final class LiveStore<T> {

    private final ObservableList<T> items = FXCollections.observableArrayList();
    private final Map<Object, T> savedSnapshots = new HashMap<>();
    private final Map<Object, T> pendingDeletions = new HashMap<>();
    private final ReadOnlyIntegerWrapper dirtyCount = new ReadOnlyIntegerWrapper(0);
    // A plain monotonically-increasing counter, not a Runnable listener list:
    // every refresh() bumps it by exactly 1, so it never repeats a value and
    // a standard JavaFX property invalidation always fires - no bespoke
    // "always notify" event plumbing needed, just an ordinary
    // ReadOnlyIntegerProperty subscribers observe the normal way.
    private final ReadOnlyIntegerWrapper refreshTick = new ReadOnlyIntegerWrapper(0);

    private final Supplier<List<T>> loader;
    private final Consumer<T> stage;
    private final Consumer<T> unstage;
    private final Function<T, Object> identity;
    private final BiPredicate<T, T> equivalence;

    /**
     * @param loader      reads the repository's current staged state
     *                    (e.g. {@code repo::findAll})
     * @param stage       stages an upsert into the repository cache
     *                    (e.g. {@code repo::save} - cache-only, no disk I/O)
     * @param unstage     stages a removal into the repository cache
     *                    (e.g. {@code item -> repo.delete(item.id())})
     * @param identity    stable key for a row across edits and reloads
     * @param equivalence whether two values count as "unchanged" for dirty
     *                    tracking (natural equality unless a field's order
     *                    isn't semantically significant)
     */
    public LiveStore(Supplier<List<T>> loader, Consumer<T> stage, Consumer<T> unstage,
                     Function<T, Object> identity, BiPredicate<T, T> equivalence) {
        this.loader = loader;
        this.stage = stage;
        this.unstage = unstage;
        this.identity = identity;
        this.equivalence = equivalence;
        refresh();
    }

    /** The live item list; bind tables/lists to it directly (never replace or copy it). */
    public ObservableList<T> items() {
        return items;
    }

    /**
     * Number of rows whose live value differs from its last-flushed snapshot
     * (or is a not-yet-flushed new row), plus removals of previously-flushed
     * rows. Bind a "Save all" button's {@code disableProperty} to a sum of
     * these reaching 0.
     */
    public ReadOnlyIntegerProperty dirtyCountProperty() {
        return dirtyCount.getReadOnlyProperty();
    }

    /** The item's stable identity key (the {@code identity} function applied to it). */
    public Object identityOf(T item) {
        return identity.apply(item);
    }

    /**
     * The last-flushed value for the row sharing {@code item}'s identity, or
     * {@code null} if it has none yet (a not-yet-flushed new row). Use as an
     * editor's dirty-comparison baseline instead of the (possibly already
     * live-edited) current value.
     */
    public @Nullable T savedSnapshot(T item) {
        return savedSnapshots.get(identity.apply(item));
    }

    /**
     * Pushes a freshly rebuilt value for the row identified by
     * {@code identityOf(updated)} into the live list and stages it into the
     * repository - no disk write.
     *
     * @return the row's index in {@link #items()}, or {@code -1} if no row
     *         with that identity exists (nothing changed)
     */
    public int updateLive(T updated) {
        Object key = identity.apply(updated);
        for (int i = 0; i < items.size(); i++) {
            if (key.equals(identity.apply(items.get(i)))) {
                items.set(i, updated);
                stage.accept(updated);
                recomputeDirtyCount();
                return i;
            }
        }
        return -1;
    }

    /**
     * Inserts a new row at the top of the list and stages it into the
     * repository, so it is instantly visible to every repository reader - a
     * normal (dirty) row from this point on.
     */
    public void insertFirst(T stub) {
        items.addFirst(stub);
        stage.accept(stub);
        recomputeDirtyCount();
    }

    /**
     * Removes a row from the live list and stages its removal into the
     * repository. A row with a flushed snapshot keeps counting as dirty until
     * the next flush; a never-flushed row is just dropped.
     */
    public void remove(T item) {
        Object key = identity.apply(item);
        items.remove(item);
        unstage.accept(item);
        if (savedSnapshots.containsKey(key)) {
            pendingDeletions.put(key, item);
        }
        recomputeDirtyCount();
    }

    /**
     * Merges {@code incoming} into the live list: an item sharing an existing
     * row's identity replaces it in place, everything else is appended - each
     * staged into the repository, same as a manual edit. For bulk
     * generate/import actions; does not touch disk.
     */
    public void mergeLive(List<T> incoming) {
        for (T item : incoming) {
            Object key = identity.apply(item);
            int index = -1;
            for (int i = 0; i < items.size(); i++) {
                if (key.equals(identity.apply(items.get(i)))) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) {
                items.set(index, item);
            } else {
                items.add(item);
            }
            stage.accept(item);
        }
        recomputeDirtyCount();
    }

    /**
     * Re-mirrors the repository's current staged state and re-baselines the
     * dirty tracking against it, then bumps {@link #refreshTickProperty()}.
     * Only meaningful right after a repository-level flush or reload (when
     * staged state and disk agree) - calling it at any other moment would
     * wrongly re-baseline unflushed edits as clean.
     */
    public void refresh() {
        List<T> loaded = loader.get();
        items.setAll(loaded);
        savedSnapshots.clear();
        for (T item : loaded) {
            savedSnapshots.put(identity.apply(item), item);
        }
        pendingDeletions.clear();
        recomputeDirtyCount();
        refreshTick.set(refreshTick.get() + 1);
    }

    /**
     * Increments by 1 at the end of every {@link #refresh()} (i.e. after a
     * global Save all or Load re-baselined this store) - subscribe with a
     * plain {@code refreshTickProperty().subscribe(...)} or
     * {@code addListener(...)}. A counter, not the re-baselined value itself,
     * so a standard JavaFX property change is guaranteed to fire every time
     * (a monotonic increment is never equal to its previous value); the store
     * outlives UI rebuilds - a subscriber that is itself rebuilt (e.g. a
     * workbench module) must unsubscribe (the {@link javafx.util.Subscription}
     * returned by {@code subscribe(...)}) when discarded, or it leaks.
     */
    public ReadOnlyIntegerProperty refreshTickProperty() {
        return refreshTick.getReadOnlyProperty();
    }

    private boolean isDirty(T item) {
        T snapshot = savedSnapshots.get(identity.apply(item));
        return snapshot == null || !equivalence.test(item, snapshot);
    }

    private void recomputeDirtyCount() {
        int count = pendingDeletions.size();
        for (T item : items) {
            if (isDirty(item)) {
                count++;
            }
        }
        dirtyCount.set(count);
    }
}
