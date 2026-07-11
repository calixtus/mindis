package org.mindis.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

/// Unit tests for the write-through/dirty-tracking engine every CRUD module
/// (Roles/Servers/Templates/Services) shares - a bug here silently corrupts
/// every screen, so it's covered directly rather than only through whichever
/// GUI module happens to exercise it. {@code repo} stands in for a real
/// repository's cache: {@code stage}/{@code unstage} write straight into it
/// (no disk I/O, matching a repository's own save()/delete()), so assertions
/// can check "did this write through" the same way a solver or CSV mapper
/// reading the repository directly would observe it.
class LiveStoreTest {

    private record Item(String id, String value) {
    }

    private final List<Item> repo = new ArrayList<>();

    private LiveStore<Item> newStore() {
        return new LiveStore<>(
                () -> List.copyOf(repo),
                item -> {
                    repo.removeIf(existing -> existing.id().equals(item.id()));
                    repo.add(item);
                },
                item -> repo.removeIf(existing -> existing.id().equals(item.id())),
                Item::id,
                Objects::equals);
    }

    @Test
    void identityOfAppliesTheIdentityFunction() {
        LiveStore<Item> store = newStore();
        assertEquals("a", store.identityOf(new Item("a", "1")));
    }

    @Test
    void newRowIsDirtyUntilFlushedViaRefresh() {
        LiveStore<Item> store = newStore();
        store.insertFirst(new Item("a", "1"));

        assertEquals(1, store.dirtyCountProperty().get());
        assertEquals(List.of("1"), repo.stream().map(Item::value).toList());

        // Simulate the repository-level flush (disk write) happening
        // elsewhere, then re-baseline via refresh() - the only path that's
        // supposed to clear dirty state.
        store.refresh();
        assertEquals(0, store.dirtyCountProperty().get());
    }

    @Test
    void updateLiveWritesThroughAndReturnsTheRowsIndex() {
        LiveStore<Item> store = newStore();
        store.insertFirst(new Item("a", "1"));
        store.refresh();

        int index = store.updateLive(new Item("a", "2"));

        assertEquals(0, index);
        assertEquals("2", store.items().get(0).value());
        assertEquals("2", repo.get(0).value(), "updateLive must stage into the repo, not just the observable list");
        assertEquals(1, store.dirtyCountProperty().get());
    }

    @Test
    void updateLiveReturnsMinusOneForUnknownIdentity() {
        LiveStore<Item> store = newStore();
        assertEquals(-1, store.updateLive(new Item("missing", "x")));
    }

    @Test
    void removeOfFlushedRowStaysDirtyUntilNextFlush() {
        LiveStore<Item> store = newStore();
        store.insertFirst(new Item("a", "1"));
        store.refresh();

        store.remove(store.items().get(0));

        assertTrue(store.items().isEmpty());
        assertTrue(repo.isEmpty());
        assertEquals(1, store.dirtyCountProperty().get(),
                "a pending deletion of a previously-flushed row still counts as an unsaved change");
    }

    @Test
    void removeOfNeverFlushedRowIsNotDirty() {
        LiveStore<Item> store = newStore();
        store.insertFirst(new Item("a", "1"));

        store.remove(store.items().get(0));

        assertEquals(0, store.dirtyCountProperty().get(), "a row that was never saved leaves nothing pending once dropped");
    }

    @Test
    void mergeLiveReplacesByIdentityAndAppendsUnmatchedRows() {
        LiveStore<Item> store = newStore();
        store.insertFirst(new Item("a", "1"));
        store.refresh();

        store.mergeLive(List.of(new Item("a", "updated"), new Item("b", "new")));

        assertEquals(2, store.items().size());
        assertEquals("updated", store.items().get(0).value());
        assertEquals("new", store.items().get(1).value());
        assertEquals(2, store.dirtyCountProperty().get());
    }

    @Test
    void refreshBumpsRefreshTickAndRebaselinesDirtyState() {
        LiveStore<Item> store = newStore();
        int before = store.refreshTickProperty().get();

        store.insertFirst(new Item("a", "1"));
        store.refresh();

        assertEquals(before + 1, store.refreshTickProperty().get());
        assertEquals(0, store.dirtyCountProperty().get());
    }

    @Test
    void savedSnapshotIsNullForANeverFlushedRow() {
        LiveStore<Item> store = newStore();
        Item stub = new Item("a", "1");
        store.insertFirst(stub);

        assertNull(store.savedSnapshot(stub));
    }

    @Test
    void savedSnapshotReflectsTheLastFlushedValueNotTheLiveEdit() {
        LiveStore<Item> store = newStore();
        store.insertFirst(new Item("a", "1"));
        store.refresh();
        store.updateLive(new Item("a", "2"));

        Item snapshot = store.savedSnapshot(store.items().get(0));
        assertNotNull(snapshot);
        assertEquals("1", snapshot.value());
    }
}
