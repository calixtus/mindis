package org.mindis.core.export;

import java.util.Arrays;

import org.jspecify.annotations.Nullable;

/// The encoded bytes of a PNG image, immutable.
///
/// A `byte[]` record component would leave the bytes writable by whoever holds
/// the record and make the record's `equals` compare arrays by identity, so the
/// records that carry a logo hold one of these instead.
public final class Png {

    private final byte[] bytes;

    private Png(byte[] bytes) {
        this.bytes = bytes;
    }

    /// A copy of `bytes`; later changes to the array do not reach the image.
    public static Png of(byte[] bytes) {
        return new Png(bytes.clone());
    }

    /// A fresh copy of the encoded bytes, for a library that wants an array.
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Png png && Arrays.equals(bytes, png.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "Png[" + bytes.length + " bytes]";
    }
}
