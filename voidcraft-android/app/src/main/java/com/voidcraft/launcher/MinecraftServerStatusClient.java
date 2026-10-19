package com.voidcraft.launcher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

/**
 * Minimal Minecraft: Java Edition status protocol client.
 *
 * Runs on a worker thread. It uses bounded packet reads and socket timeouts;
 * it does not persist the result because a server's status can change at any time.
 */
final class MinecraftServerStatusClient {
    private static final int DEFAULT_PORT = 25565;
    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 3_000;
    private static final int MAX_PACKET_BYTES = 1_048_576;
    private static final int MAX_HOST_BYTES = 255;
    private static final int MAX_FAVICON_CHARS = 200_000;
    private static final int STATUS_PROTOCOL_VERSION = 47;

    static final class Result {
        final boolean online;
        final String message;
        final String motd;
        final String version;
        final int playersOnline;
        final int playersMax;
        final String faviconDataUri;

        private Result(boolean online, String message, String motd, String version,
                       int playersOnline, int playersMax, String faviconDataUri) {
            this.online = online;
            this.message = message;
            this.motd = motd;
            this.version = version;
            this.playersOnline = playersOnline;
            this.playersMax = playersMax;
            this.faviconDataUri = faviconDataUri;
        }

        static Result online(String motd, String version, int playersOnline,
                             int playersMax, String faviconDataUri) {
            return new Result(true, "", motd, version, playersOnline, playersMax, faviconDataUri);
        }

        static Result offline(String message) {
            return new Result(false, message, "", "", -1, -1, null);
        }
    }

    private static final class Endpoint {
        final String host;
        final int port;

        Endpoint(String host, int port) {
            this.host = host;
            this.port = port;
        }

        static Endpoint parse(String address) {
            if (address == null) {
                throw new IllegalArgumentException("Enter a server address.");
            }
            String value = address.trim();
            String host;
            int port = DEFAULT_PORT;

            if (value.startsWith("[")) {
                int closingBracket = value.indexOf(']');
                if (closingBracket < 2) {
                    throw new IllegalArgumentException("Invalid bracketed IPv6 address.");
                }
                host = value.substring(1, closingBracket);
                String suffix = value.substring(closingBracket + 1);
                if (!suffix.isEmpty()) {
                    if (!suffix.startsWith(":") || suffix.length() == 1) {
                        throw new IllegalArgumentException("Use [IPv6]:port for a custom port.");
                    }
                    port = parsePort(suffix.substring(1));
                }
            } else {
                int firstColon = value.indexOf(':');
                int lastColon = value.lastIndexOf(':');
                if (firstColon >= 0 && firstColon == lastColon) {
                    host = value.substring(0, firstColon);
                    port = parsePort(value.substring(firstColon + 1));
                } else {
                    // Multiple colons are treated as an unbracketed IPv6 literal.
                    host = value;
                }
            }

            if (host.isEmpty() || host.length() > 255
                    || host.contains("/") || host.contains("://")) {
                throw new IllegalArgumentException("Enter a hostname or IP address.");
            }
            for (int index = 0; index < host.length(); index++) {
                if (Character.isWhitespace(host.charAt(index))) {
                    throw new IllegalArgumentException("Server addresses cannot contain spaces.");
                }
            }
            if (host.getBytes(StandardCharsets.UTF_8).length > MAX_HOST_BYTES) {
                throw new IllegalArgumentException("Server address is too long.");
            }
            return new Endpoint(host, port);
        }

        private static int parsePort(String rawPort) {
            try {
                int parsed = Integer.parseInt(rawPort);
                if (parsed < 1 || parsed > 65535) {
                    throw new IllegalArgumentException("Port must be between 1 and 65535.");
                }
                return parsed;
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Server port must be a number from 1 to 65535.");
            }
        }
    }

    private MinecraftServerStatusClient() {
    }

    static Result query(String address) {
        final Endpoint endpoint;
        try {
            endpoint = Endpoint.parse(address);
        } catch (IllegalArgumentException error) {
            return Result.offline(error.getMessage());
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(endpoint.host, endpoint.port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);

            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            DataInputStream input = new DataInputStream(socket.getInputStream());

            ByteArrayOutputStream handshakeBytes = new ByteArrayOutputStream();
            DataOutputStream handshake = new DataOutputStream(handshakeBytes);
            writeVarInt(handshake, 0); // Handshake packet ID.
            writeVarInt(handshake, STATUS_PROTOCOL_VERSION);
            writeProtocolString(handshake, endpoint.host);
            handshake.writeShort(endpoint.port);
            writeVarInt(handshake, 1); // Next protocol state: status.
            writeFrame(output, handshakeBytes.toByteArray());

            ByteArrayOutputStream requestBytes = new ByteArrayOutputStream();
            DataOutputStream request = new DataOutputStream(requestBytes);
            writeVarInt(request, 0); // Status request packet ID.
            writeFrame(output, requestBytes.toByteArray());
            output.flush();

            int frameLength = readVarInt(input);
            if (frameLength < 1 || frameLength > MAX_PACKET_BYTES) {
                throw new IOException("Server status packet size is invalid.");
            }
            byte[] frame = new byte[frameLength];
            input.readFully(frame);

            DataInputStream packet = new DataInputStream(new ByteArrayInputStream(frame));
            int packetId = readVarInt(packet);
            if (packetId != 0) {
                throw new IOException("Server returned an unexpected status packet.");
            }
            String json = readProtocolString(packet);
            JSONObject root = new JSONObject(json);

            JSONObject versionObject = root.optJSONObject("version");
            String version = versionObject == null ? "" : versionObject.optString("name", "");
            version = limit(cleanDisplayText(version), 80);

            JSONObject playersObject = root.optJSONObject("players");
            int online = playersObject == null ? -1 : nonNegative(playersObject.optInt("online", -1));
            int maximum = playersObject == null ? -1 : nonNegative(playersObject.optInt("max", -1));

            String motd = cleanDisplayText(flattenText(root.opt("description")));
            if (motd.isEmpty()) {
                motd = "No server description provided.";
            }
            motd = limit(motd, 240);

            String favicon = root.opt("favicon") instanceof String
                    ? (String) root.opt("favicon") : null;
            if (favicon != null && (favicon.length() > MAX_FAVICON_CHARS
                    || !favicon.startsWith("data:image/png;base64,"))) {
                favicon = null;
            }

            return Result.online(motd, version, online, maximum, favicon);
        } catch (SocketTimeoutException error) {
            return Result.offline("Timed out waiting for the server.");
        } catch (UnknownHostException error) {
            return Result.offline("The hostname could not be resolved.");
        } catch (ConnectException error) {
            return Result.offline("The server refused or could not accept the connection.");
        } catch (JSONException error) {
            return Result.offline("The server returned an invalid status response.");
        } catch (EOFException error) {
            return Result.offline("The server closed the connection before returning status.");
        } catch (IOException error) {
            return Result.offline("Could not read server status: " + safeMessage(error));
        } catch (SecurityException error) {
            return Result.offline("Network access is not permitted by this device.");
        }
    }

    private static String safeMessage(IOException error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "connection failed.";
        }
        String compact = message.replace('\n', ' ').replace('\r', ' ').trim();
        return limit(compact, 100);
    }

    private static int nonNegative(int value) {
        return value < 0 ? -1 : value;
    }

    private static String flattenText(Object component) {
        StringBuilder output = new StringBuilder();
        appendText(output, component);
        return output.toString();
    }

    private static void appendText(StringBuilder output, Object component) {
        if (component instanceof String) {
            output.append((String) component);
            return;
        }
        if (component instanceof JSONArray) {
            JSONArray array = (JSONArray) component;
            for (int index = 0; index < array.length(); index++) {
                appendText(output, array.opt(index));
            }
            return;
        }
        if (component instanceof JSONObject) {
            JSONObject object = (JSONObject) component;
            Object text = object.opt("text");
            if (text instanceof String) {
                output.append((String) text);
            } else {
                Object translate = object.opt("translate");
                if (translate instanceof String && ((String) translate).length() < 100) {
                    output.append((String) translate);
                }
            }
            appendText(output, object.opt("extra"));
        }
    }

    private static String cleanDisplayText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\u00a7' && index + 1 < value.length()) {
                index++; // Skip the Minecraft legacy formatting code.
            } else if (current == '\r' || current == '\n') {
                cleaned.append(" • ");
            } else if (!Character.isISOControl(current)) {
                cleaned.append(current);
            }
            if (cleaned.length() >= 300) {
                break;
            }
        }
        return cleaned.toString().trim();
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }

    private static void writeFrame(DataOutputStream output, byte[] payload) throws IOException {
        if (payload.length < 1 || payload.length > MAX_PACKET_BYTES) {
            throw new IOException("Outgoing status packet size is invalid.");
        }
        writeVarInt(output, payload.length);
        output.write(payload);
    }

    private static void writeProtocolString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_HOST_BYTES) {
            throw new IOException("Server address is too long for the status protocol.");
        }
        writeVarInt(output, bytes.length);
        output.write(bytes);
    }

    private static String readProtocolString(DataInputStream input) throws IOException {
        int length = readVarInt(input);
        if (length < 0 || length > MAX_PACKET_BYTES || length > input.available()) {
            throw new IOException("Server status text length is invalid.");
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readVarInt(DataInputStream input) throws IOException {
        int result = 0;
        int bytesRead = 0;
        int current;
        do {
            current = input.readUnsignedByte();
            result |= (current & 0x7F) << (7 * bytesRead);
            bytesRead++;
            if (bytesRead > 5) {
                throw new IOException("Invalid variable-length integer from server.");
            }
        } while ((current & 0x80) != 0);
        if (result < 0) {
            throw new IOException("Negative packet size from server.");
        }
        return result;
    }

    private static void writeVarInt(DataOutputStream output, int value) throws IOException {
        do {
            int part = value & 0x7F;
            value >>>= 7;
            if (value != 0) {
                part |= 0x80;
            }
            output.writeByte(part);
        } while (value != 0);
    }
}
