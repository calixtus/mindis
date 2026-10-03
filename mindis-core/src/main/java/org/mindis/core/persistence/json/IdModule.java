package org.mindis.core.persistence.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.function.Function;

import org.mindis.core.model.RoleId;
import org.mindis.core.model.ServerId;

/// Writes the typed ids as the bare strings they were before they had types,
/// so the document format did not change with them: `"role": "ACOLYTE"`, not
/// `"role": {"value": "ACOLYTE"}`. A module rather than annotations on the ids,
/// for the reason [RecurrenceRuleMixin] gives.
public final class IdModule extends SimpleModule {

    public IdModule() {
        super("MinDisIds");
        add(RoleId.class, RoleId::new, RoleId::value);
        add(ServerId.class, ServerId::new, ServerId::value);
    }

    private <T> void add(Class<T> type, Function<String, T> fromValue, Function<T, String> toValue) {
        addSerializer(type, new StdSerializer<>(type) {
            @Override
            public void serialize(T id, JsonGenerator generator, SerializerProvider provider) throws IOException {
                generator.writeString(toValue.apply(id));
            }
        });
        addDeserializer(type, new FromStringDeserializer<>(type) {
            @Override
            protected T _deserialize(String value, DeserializationContext context) {
                return fromValue.apply(value);
            }
        });
    }
}
