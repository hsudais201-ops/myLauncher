package com.mf.mylauncher.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import javax.net.ssl.HttpsURLConnection;

/** Bounded HTTPS client for Mojang's public version-manifest endpoint. */
public final class VersionManifestDownloader {
    public static final URI MANIFEST_URI = URI.create(
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    public static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    public static final int READ_TIMEOUT_MILLIS = 15_000;
    public static final int MAX_MANIFEST_BYTES = 8 * 1024 * 1024;
    private static final int BUFFER_SIZE = 8 * 1024;

    private final ConnectionOpener opener;

    public VersionManifestDownloader() {
        this(JdkManifestConnection::new);
    }

    VersionManifestDownloader(ConnectionOpener opener) {
        this.opener = Objects.requireNonNull(opener, "connection opener");
    }

    /** Downloads Mojang's manifest and returns a validated domain model. */
    public VersionManifest downloadManifest() throws IOException {
        String json = download();
        try {
            return VersionManifestJsonDecoder.decode(json);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Mojang version manifest JSON was invalid", exception);
        }
    }

    /** Downloads and returns strict UTF-8 JSON text for callers that need raw metadata. */
    public String download() throws IOException {
        ManifestConnection connection = opener.open(MANIFEST_URI);
        if (connection == null) throw new IOException("Manifest connection could not be opened");
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "myLauncher/0.1 (Android; Minecraft version manifest)");

            int status = connection.getResponseCode();
            if (status != 200) {
                throw new IOException("Mojang version manifest request failed with HTTP " + status);
            }
            long declaredLength = connection.getContentLengthLong();
            if (declaredLength > MAX_MANIFEST_BYTES) {
                throw new IOException("Mojang version manifest exceeds the 8 MiB size limit");
            }

            byte[] body;
            try (InputStream stream = connection.getInputStream()) {
                body = readBounded(stream, MAX_MANIFEST_BYTES);
            }
            if (body.length == 0) throw new IOException("Mojang version manifest response was empty");

            final String json;
            try {
                json = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(body)).toString();
            } catch (CharacterCodingException exception) {
                throw new IOException("Mojang version manifest was not valid UTF-8", exception);
            }
            if (!json.trim().startsWith("{")) {
                throw new IOException("Mojang version manifest response was not a JSON object");
            }
            return json;
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readBounded(InputStream input, int maximumBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(32 * 1024, maximumBytes));
        byte[] buffer = new byte[BUFFER_SIZE];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > maximumBytes) {
                throw new IOException("Mojang version manifest exceeds the 8 MiB size limit");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    interface ConnectionOpener {
        ManifestConnection open(URI uri) throws IOException;
    }

    interface ManifestConnection {
        void setConnectTimeout(int timeoutMillis);
        void setReadTimeout(int timeoutMillis);
        void setInstanceFollowRedirects(boolean follow);
        void setRequestMethod(String method) throws IOException;
        void setRequestProperty(String key, String value);
        int getResponseCode() throws IOException;
        long getContentLengthLong();
        InputStream getInputStream() throws IOException;
        void disconnect();
    }

    private static final class JdkManifestConnection implements ManifestConnection {
        private final HttpsURLConnection delegate;

        JdkManifestConnection(URI uri) throws IOException {
            if (uri == null || !MANIFEST_URI.equals(uri) || !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IOException("Refusing to connect to an unapproved manifest URL");
            }
            URL url = uri.toURL();
            this.delegate = (HttpsURLConnection) url.openConnection();
        }

        @Override public void setConnectTimeout(int timeoutMillis) { delegate.setConnectTimeout(timeoutMillis); }
        @Override public void setReadTimeout(int timeoutMillis) { delegate.setReadTimeout(timeoutMillis); }
        @Override public void setInstanceFollowRedirects(boolean follow) { delegate.setInstanceFollowRedirects(follow); }
        @Override public void setRequestMethod(String method) throws IOException { delegate.setRequestMethod(method); }
        @Override public void setRequestProperty(String key, String value) { delegate.setRequestProperty(key, value); }
        @Override public int getResponseCode() throws IOException { return delegate.getResponseCode(); }
        @Override public long getContentLengthLong() { return delegate.getContentLengthLong(); }
        @Override public InputStream getInputStream() throws IOException { return delegate.getInputStream(); }
        @Override public void disconnect() { delegate.disconnect(); }
    }
}
