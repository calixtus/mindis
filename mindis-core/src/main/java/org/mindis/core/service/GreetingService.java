package org.mindis.core.service;

import jakarta.inject.Singleton;

import org.mindis.core.l10n.Localization;

/**
 * Placeholder service proving core-to-gui dependency injection in the M0 spike.
 * Replaced by real services from M2 on.
 */
@Singleton
public class GreetingService {

    public String welcomeMessage() {
        return Localization.lang("Welcome to MinDis");
    }
}
