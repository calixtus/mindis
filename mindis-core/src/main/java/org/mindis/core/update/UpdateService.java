package org.mindis.core.update;

import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Optional;
import java.util.function.LongConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Asks the release server whether a newer MinDis was published, downloads its
/// installer and hands it to the operating system
/// (docs/adr/010-auto-update.md).
///
/// <p>Deliberately thin: every decision lives in a pure, tested helper
/// ([AppVersion], [UpdateManifests], [Checksums],
/// [InstallerLauncher]) and what remains here is HTTP plumbing. Nothing in
/// this class touches the UI - the caller runs it off the FX thread and reports
/// the outcome (PLAN.md section 2.5).
///
/// <p>Not `final` so a test can subclass it as a stub, like
/// [org.mindis.core.preferences.PreferencesService].
@Singleton
public class UpdateService {

    /// Where the manifests are published. `releases/latest/download/…`
    /// always resolves against the newest non-draft release, so the URL never
    /// changes with a release.
    public static final String DEFAULT_MANIFEST_BASE_URL =
            "https://github.com/calixtus/mindis/releases/latest/download/";

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateService.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final String manifestBaseUrl;

    public UpdateService() {
        this(DEFAULT_MANIFEST_BASE_URL);
    }

    protected UpdateService(String manifestBaseUrl) {
        this.manifestBaseUrl = manifestBaseUrl.endsWith("/") ? manifestBaseUrl : manifestBaseUrl + "/";
    }

    /// True when this build could install an update at all: a known platform
    /// and a known running version. False for a run from sources, where the
    /// check is pointless rather than broken.
    public boolean isSupported() {
        return UpdatePlatform.current().isPresent() && AppVersion.current().isPresent();
    }

    /// Fetches this platform's manifest and compares it with the running
    /// version. Blocking; call it off the UI thread.
    ///
    /// @return the newer release, or empty when this is the newest version,
    ///         the platform publishes no installer, or the build has no
    ///         version of its own
    /// @throws IOException when the manifest cannot be fetched or read - the
    ///         caller decides whether that is worth a dialog (a manual check)
    ///         or a log line (the check on startup)
    public Optional<AvailableUpdate> check() throws IOException {
        Optional<UpdatePlatform> platform = UpdatePlatform.current();
        Optional<AppVersion> running = AppVersion.current();
        if (platform.isEmpty() || running.isEmpty()) {
            LOGGER.debug("Update check skipped: platform={}, version={}", platform, running);
            return Optional.empty();
        }
        String url = manifestBaseUrl + platform.get().manifestFileName();
        LOGGER.debug("Checking for updates: {}", url);
        UpdateManifest manifest = UpdateManifests.parse(get(url, HttpResponse.BodyHandlers.ofString()));
        return UpdateManifests.available(manifest, running.get());
    }

    /// Downloads `artifact` into a fresh temporary directory and verifies
    /// its SHA-256. Blocking; call it off the UI thread.
    ///
    /// @param progress called with the number of bytes received so far, for a
    ///                 progress display; compare against
    ///                 [UpdateArtifact#size]
    /// @return the verified file, ready for [#install]
    /// @throws IOException on a transport failure *and* on a checksum
    ///         mismatch; the partial file is removed either way, so a failed
    ///         download never leaves something installable behind
    public Path download(UpdateArtifact artifact, LongConsumer progress) throws IOException {
        Path directory = Files.createTempDirectory("mindis-update");
        Path target = directory.resolve(artifact.fileName());
        try {
            try (InputStream body = get(artifact.url(), HttpResponse.BodyHandlers.ofInputStream());
                 OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
                         StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[64 * 1024];
                long received = 0;
                for (int read = body.read(buffer); read >= 0; read = body.read(buffer)) {
                    out.write(buffer, 0, read);
                    received += read;
                    progress.accept(received);
                }
            }
            Checksums.verify(target, artifact.sha256());
            LOGGER.info("Downloaded and verified update: {}", target);
            return target;
        } catch (IOException e) {
            deleteQuietly(target);
            throw e;
        }
    }

    /// Hands the verified installer to the operating system. The caller quits
    /// MinDis right after: the installer cannot replace files that are in use.
    public void install(Path installer) throws IOException {
        UpdatePlatform platform = UpdatePlatform.current()
                .orElseThrow(() -> new IOException("No installer support for this platform"));
        InstallerLauncher.launch(platform, installer);
    }

    private <T> T get(String url, HttpResponse.BodyHandler<T> bodyHandler) throws IOException {
        // A client per request: the check runs at most twice a session, and a
        // long-lived client would keep its own connection pool and selector
        // thread alive for the whole run.
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                // GitHub serves release assets as a redirect to its CDN.
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "*/*")
                    .GET()
                    .build();
            HttpResponse<T> response = client.send(request, bodyHandler);
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " for " + url);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Update request interrupted: " + url, e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOGGER.debug("Could not remove partial download: {}", file, e);
        }
    }
}
