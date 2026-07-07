package org.mindis.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mindis.core.model.Role;

class RoleRepositoryRoundTripTest {

    @TempDir
    Path tempDir;

    @Test
    void emptyStoreSeedsDefaultRoles() {
        List<Role> roles = new RoleRepository(tempDir.resolve("roles.json")).findAll();

        assertFalse(roles.isEmpty(), "expected seeded default roles");
        assertTrue(roles.stream().anyMatch(role -> Role.ACOLYTE.equals(role.id())));
    }

    @Test
    void customRoleWithAgeSurvivesRoundTrip() {
        Path file = tempDir.resolve("roles.json");
        RoleRepository repository = new RoleRepository(file);
        Role thurifer = new Role(Role.newId(), "Thurifer", 14, 99, 50);

        repository.save(thurifer);
        Role reloaded = new RoleRepository(file).findById(thurifer.id()).orElseThrow();

        assertEquals(thurifer, reloaded);
        assertEquals(14, reloaded.minAge());
        assertEquals(99, reloaded.maxAge());
    }
}
