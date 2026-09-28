package org.mindis.core.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/// SHA-256 of a downloaded file. A downloaded installer is executed, so it is
/// verified against the checksum the manifest carries before it is ever handed
/// to the operating system - a truncated download or a substituted file must
/// not reach jpackage's installer.
public final class Checksums {

    private Checksums() {
    }

    public static String sha256(Path file) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream in = Files.newInputStream(file);
             DigestInputStream digestIn = new DigestInputStream(in, digest)) {
            byte[] buffer = new byte[8192];
            while (digestIn.read(buffer) >= 0) {
                // Reading is the point; DigestInputStream does the work.
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /// @throws IOException when the file's checksum is not `expected` -
    ///         an IOException (not an assertion) because a corrupted or
    ///         tampered download is an ordinary runtime outcome the caller has
    ///         to report to the user.
    public static void verify(Path file, String expected) throws IOException {
        String actual = sha256(file);
        if (!actual.equals(expected.strip().toLowerCase(Locale.ROOT))) {
            throw new IOException("Checksum mismatch for " + file.getFileName()
                    + ": expected " + expected + ", got " + actual);
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Every JRE ships SHA-256 (JLS-mandated MessageDigest algorithm).
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
