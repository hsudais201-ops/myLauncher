package com.mf.mylauncher.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds a Java argv list without shell concatenation; callers still need a compatible runtime and login. */
public final class LaunchCommandBuilder {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$?\\{([A-Za-z0-9_]+)\\}");

    private LaunchCommandBuilder() { }

    /** Returns JVM options, the main class, then game arguments as individual argv entries. */
    public static List<String> build(VersionDetails details, LaunchContext context) {
        if (details == null) throw new IllegalArgumentException("Minecraft version details are required");
        if (context == null) throw new IllegalArgumentException("Launch context is required");
        LibraryPlatform platform = context.getPlatform();
        Map<String, String> variables = context.variables(details);
        VersionArgumentSet argumentSet = details.getArguments();
        List<String> configuredJvm = argumentSet.resolveJvmArguments(platform);
        List<String> configuredGame = argumentSet.resolveGameArguments(platform);
        if (configuredGame.isEmpty() && details.getLegacyMinecraftArguments() == null) {
            throw new IllegalArgumentException("Version metadata contains no game arguments");
        }

        List<String> command = new ArrayList<>();
        if (!containsPrefix(configuredJvm, "-Xms")) command.add("-Xms" + context.getMinHeapMiB() + "M");
        if (!containsPrefix(configuredJvm, "-Xmx")) command.add("-Xmx" + context.getMaxHeapMiB() + "M");
        if (!containsPrefix(configuredJvm, "-Djava.library.path=")) {
            command.add("-Djava.library.path=" + variables.get("natives_directory"));
        }
        if (configuredJvm.isEmpty()) command.add("-Dfile.encoding=UTF-8");
        for (String arg : configuredJvm) command.add(expand(arg, variables));
        if (!hasClasspathOption(configuredJvm)) {
            command.add("-cp");
            command.add(variables.get("classpath"));
        }
        command.add(details.getMainClass());

        List<String> gameArguments = configuredGame.isEmpty()
                ? tokenizeLegacyArguments(details.getLegacyMinecraftArguments()) : configuredGame;
        for (String arg : gameArguments) command.add(expand(arg, variables));
        return Collections.unmodifiableList(command);
    }

    private static boolean containsPrefix(List<String> args, String prefix) {
        for (String arg : args) if (arg.startsWith(prefix)) return true;
        return false;
    }

    private static boolean hasClasspathOption(List<String> args) {
        for (String arg : args) if ("-cp".equals(arg) || "-classpath".equals(arg)) return true;
        return false;
    }

    private static String expand(String source, Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER.matcher(source);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = variables.get(matcher.group(1));
            if (value == null) throw new IllegalArgumentException("Unsupported launch argument placeholder: " + matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** Minimal tokenizer for old minecraftArguments metadata: quotes group tokens and are not passed on. */
    static List<String> tokenizeLegacyArguments(String input) {
        if (input == null) throw new IllegalArgumentException("Legacy Minecraft arguments are missing");
        List<String> result = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        boolean tokenStarted = false;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (escaped) {
                token.append(ch); escaped = false; tokenStarted = true; continue;
            }
            if (ch == '\\' && quote != '\'') {
                escaped = true; tokenStarted = true; continue;
            }
            if ((ch == '"' || ch == '\'') && (quote == 0 || quote == ch)) {
                quote = quote == 0 ? ch : 0; tokenStarted = true; continue;
            }
            if (Character.isWhitespace(ch) && quote == 0) {
                if (tokenStarted) { result.add(token.toString()); token.setLength(0); tokenStarted = false; }
                continue;
            }
            token.append(ch); tokenStarted = true;
        }
        if (escaped) token.append('\\');
        if (quote != 0) throw new IllegalArgumentException("Legacy Minecraft arguments contain an unclosed quote");
        if (tokenStarted) result.add(token.toString());
        return Collections.unmodifiableList(result);
    }
}
