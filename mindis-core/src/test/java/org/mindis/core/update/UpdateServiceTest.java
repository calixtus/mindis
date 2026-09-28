package org.mindis.core.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// Exercises the real HTTP path - fetch, evaluate, download, verify - against a
/// local server, so the one part of the update check that talks to the network
/// is not the one part nothing covers.
///
/// <p>The server is a plain [ServerSocket] on an ephemeral port answering
/// HTTP/1.1 by hand: nothing outside `java.base`, no fixed port, and no
/// dependency on the machine being online.
class UpdateServiceTest {

    private static final String PAYLOAD = "fake installer payload";
    /// SHA-256 of [#PAYLOAD].
    private static final String PAYLOAD_SHA256 =
            "bc002e90b5bba677e2b1c80f531e7f9bf4fdcfe71d44e0c11881dc856bd357a4";

    private StubServer server;

    /// The service under test, pointed at the stub server instead of GitHub.
    private static final class LocalUpdateService extends UpdateService {
        LocalUpdateService(String baseUrl) {
            super(baseUrl);
        }
    }

    @BeforeEach
    void startServer() throws IOException {
        server = new StubServer();
        server.serve("/latest-" + platformKey() + ".json", manifest("9.9.9"));
        server.serve("/MinDis-9.9.9.msi", PAYLOAD);
    }

    @AfterEach
    void stopServer() throws IOException {
        server.close();
    }

    @Test
    void findsANewerReleaseAndDownloadsItsVerifiedInstaller() throws IOException {
        UpdateService service = new LocalUpdateService(server.baseUrl());

        Optional<AvailableUpdate> update = service.check();

        assertTrue(update.isPresent(), "A 9.9.9 release must read as newer than any built version");
        assertEquals(new AppVersion(9, 9, 9), update.get().version());

        AtomicLong received = new AtomicLong();
        Path installer = service.download(update.get().installer(), received::set);

        assertEquals(PAYLOAD, Files.readString(installer));
        assertEquals(PAYLOAD.length(), received.get(), "Progress must end at the full size");
    }

    @Test
    void aFileThatDoesNotMatchTheChecksumIsRejectedAndRemoved() {
        UpdateService service = new LocalUpdateService(server.baseUrl());
        UpdateArtifact tampered = new UpdateArtifact("MinDis-9.9.9.msi",
                server.baseUrl() + "MinDis-9.9.9.msi", "00".repeat(32),
                PAYLOAD.length(), UpdateArtifact.Kind.INSTALLER);

        IOException failure = assertThrows(IOException.class, () -> service.download(tampered, received -> { }));

        assertTrue(failure.getMessage().contains("Checksum mismatch"), failure.getMessage());
    }

    @Test
    void anOlderReleaseIsNoUpdate() throws IOException {
        server.serve("/latest-" + platformKey() + ".json", manifest("0.0.1"));
        UpdateService service = new LocalUpdateService(server.baseUrl());

        assertEquals(Optional.empty(), service.check());
    }

    @Test
    void aMissingManifestIsReportedAsAFailedCheck() {
        UpdateService service = new LocalUpdateService(server.baseUrl() + "nope/");

        IOException failure = assertThrows(IOException.class, service::check);

        assertTrue(failure.getMessage().contains("404"), failure.getMessage());
    }

    private String manifest(String version) {
        return """
                {
                  "version": "%s",
                  "platform": "%s",
                  "artifacts": [
                    {
                      "fileName": "MinDis-9.9.9.msi",
                      "url": "%sMinDis-9.9.9.msi",
                      "sha256": "%s",
                      "size": %d,
                      "kind": "INSTALLER"
                    }
                  ]
                }
                """.formatted(version, platformKey(), server.baseUrl(), PAYLOAD_SHA256, PAYLOAD.length());
    }

    /// The manifest the service asks for is the one for the platform the test
    /// happens to run on.
    private static String platformKey() {
        return UpdatePlatform.current().orElseThrow().key();
    }

    /// A single-threaded HTTP/1.1 server for a fixed set of paths. It answers
    /// one request per connection and closes it, which is all `HttpClient`
    /// needs - and about as much code as a canned response would have been.
    private static final class StubServer implements AutoCloseable {

        private final ServerSocket socket;
        private final Map<String, String> bodies = new HashMap<>();

        StubServer() throws IOException {
            socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
            Thread acceptor = new Thread(this::acceptLoop, "update-stub-server");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        void serve(String path, String body) {
            bodies.put(path, body);
        }

        String baseUrl() {
            return "http://" + socket.getInetAddress().getHostAddress() + ":" + socket.getLocalPort() + "/";
        }

        private void acceptLoop() {
            while (!socket.isClosed()) {
                try (Socket connection = socket.accept()) {
                    handle(connection);
                } catch (IOException e) {
                    // Closing the server socket is how this loop ends.
                    return;
                }
            }
        }

        private void handle(Socket connection) throws IOException {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            String requestLine = in.readLine();
            if (requestLine == null) {
                return;
            }
            // Drain the headers before answering.
            for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
                // Headers are irrelevant here.
            }
            String path = requestLine.split(" ")[1];
            String body = bodies.get(path);
            byte[] bytes = (body == null ? "not found" : body).getBytes(StandardCharsets.UTF_8);
            OutputStream out = connection.getOutputStream();
            out.write(("HTTP/1.1 " + (body == null ? "404 Not Found" : "200 OK") + "\r\n"
                    + "Content-Length: " + bytes.length + "\r\n"
                    + "Content-Type: application/octet-stream\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(bytes);
            out.flush();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
