package org.mindis.gui.di;

import com.dlsc.fxmlkit.di.BaseDiAdapter;

import io.avaje.inject.BeanScope;

/**
 * Bridges FxmlKit's DI hook to Avaje Inject: controllers are resolved from the
 * application {@link BeanScope}, so they receive constructor injection. Member
 * injection is a no-op - constructor injection only (PLAN.md section 2.4).
 */
public final class AvajeDiAdapter extends BaseDiAdapter {

    private final BeanScope beanScope;

    public AvajeDiAdapter(BeanScope beanScope) {
        this.beanScope = beanScope;
    }

    @Override
    protected <T> T doGetInstance(Class<T> type) {
        return beanScope.get(type);
    }
}
