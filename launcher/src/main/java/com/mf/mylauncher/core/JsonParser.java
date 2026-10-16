package com.mf.mylauncher.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small strict JSON parser used by the dependency-free launcher core. */
final class JsonParser {
    private static final int MAX_DEPTH = 64;
    private static final int MAX_JSON_CHARACTERS = 8 * 1024 * 1024;
    private final String input;
    private int position;

    private JsonParser(String input) {
        if (input == null) throw new IllegalArgumentException("JSON input is required");
        if (input.length() > MAX_JSON_CHARACTERS) {
            throw new IllegalArgumentException("JSON input exceeds the 8 MiB character limit");
        }
        this.input = input;
    }

    static Object parse(String input) {
        JsonParser parser = new JsonParser(input);
        parser.skipWhitespace();
        Object result = parser.parseValue(0);
        parser.skipWhitespace();
        if (!parser.atEnd()) throw parser.error("Unexpected trailing content");
        return result;
    }

    private Object parseValue(int depth) {
        if (depth > MAX_DEPTH) throw error("JSON nesting exceeds the maximum depth of " + MAX_DEPTH);
        if (atEnd()) throw error("Expected a JSON value");
        char ch = input.charAt(position);
        switch (ch) {
            case '{': return parseObject(depth + 1);
            case '[': return parseArray(depth + 1);
            case '"': return parseString();
            case 't': expectKeyword("true"); return Boolean.TRUE;
            case 'f': expectKeyword("false"); return Boolean.FALSE;
            case 'n': expectKeyword("null"); return null;
            default:
                if (ch == '-' || (ch >= '0' && ch <= '9')) return parseNumber();
                throw error("Unexpected character while reading a JSON value");
        }
    }

    private Map<String, Object> parseObject(int depth) {
        position++;
        skipWhitespace();
        Map<String, Object> result = new LinkedHashMap<>();
        if (consume('}')) return result;
        while (true) {
            if (atEnd() || input.charAt(position) != '"') throw error("Expected a quoted object key");
            String key = parseString();
            skipWhitespace();
            require(':');
            skipWhitespace();
            Object value = parseValue(depth);
            if (result.containsKey(key)) throw error("Duplicate object key: " + key);
            result.put(key, value);
            skipWhitespace();
            if (consume('}')) return result;
            require(',');
            skipWhitespace();
        }
    }

    private List<Object> parseArray(int depth) {
        position++;
        skipWhitespace();
        List<Object> result = new ArrayList<>();
        if (consume(']')) return result;
        while (true) {
            result.add(parseValue(depth));
            skipWhitespace();
            if (consume(']')) return result;
            require(',');
            skipWhitespace();
        }
    }

    private String parseString() {
        require('"');
        StringBuilder result = new StringBuilder();
        while (!atEnd()) {
            char ch = input.charAt(position++);
            if (ch == '"') {
                String decoded = result.toString();
                validateSurrogatePairs(decoded);
                return decoded;
            }
            if (ch < 0x20) throw error("Unescaped control character in string");
            if (ch == '\\') {
                if (atEnd()) throw error("Unfinished escape sequence");
                char escape = input.charAt(position++);
                switch (escape) {
                    case '"': result.append('"'); break;
                    case '\\': result.append('\\'); break;
                    case '/': result.append('/'); break;
                    case 'b': result.append('\b'); break;
                    case 'f': result.append('\f'); break;
                    case 'n': result.append('\n'); break;
                    case 'r': result.append('\r'); break;
                    case 't': result.append('\t'); break;
                    case 'u': result.append(parseUnicodeEscape()); break;
                    default: throw error("Invalid string escape");
                }
            } else {
                result.append(ch);
            }
        }
        throw error("Unterminated JSON string");
    }

    private char parseUnicodeEscape() {
        if (input.length() - position < 4) throw error("Incomplete Unicode escape");
        int code = 0;
        for (int i = 0; i < 4; i++) {
            int digit = hexValue(input.charAt(position++));
            if (digit < 0) throw error("Invalid Unicode escape");
            code = (code << 4) | digit;
        }
        return (char) code;
    }

    private static int hexValue(char ch) {
        if (ch >= '0' && ch <= '9') return ch - '0';
        if (ch >= 'a' && ch <= 'f') return ch - 'a' + 10;
        if (ch >= 'A' && ch <= 'F') return ch - 'A' + 10;
        return -1;
    }

    private void validateSurrogatePairs(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isHighSurrogate(ch)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    throw error("Unpaired high surrogate in JSON string");
                }
                i++;
            } else if (Character.isLowSurrogate(ch)) {
                throw error("Unpaired low surrogate in JSON string");
            }
        }
    }

    private BigDecimal parseNumber() {
        int start = position;
        consume('-');
        if (consume('0')) {
            if (!atEnd() && isDigit(input.charAt(position))) throw error("Leading zero in number");
        } else {
            requireDigitOneToNine();
            while (!atEnd() && isDigit(input.charAt(position))) position++;
        }
        if (consume('.')) {
            if (atEnd() || !isDigit(input.charAt(position))) throw error("Fraction requires at least one digit");
            while (!atEnd() && isDigit(input.charAt(position))) position++;
        }
        if (consume('e') || consume('E')) {
            if (!consume('+')) consume('-');
            if (atEnd() || !isDigit(input.charAt(position))) throw error("Exponent requires at least one digit");
            while (!atEnd() && isDigit(input.charAt(position))) position++;
        }
        try {
            return new BigDecimal(input.substring(start, position));
        } catch (NumberFormatException exception) {
            throw error("Invalid JSON number");
        }
    }

    private void requireDigitOneToNine() {
        if (atEnd() || input.charAt(position) < '1' || input.charAt(position) > '9') {
            throw error("Expected a digit from 1 to 9");
        }
        position++;
    }

    private static boolean isDigit(char ch) { return ch >= '0' && ch <= '9'; }

    private void expectKeyword(String keyword) {
        if (!input.regionMatches(position, keyword, 0, keyword.length())) throw error("Invalid JSON keyword");
        position += keyword.length();
    }

    private void skipWhitespace() {
        while (!atEnd()) {
            char ch = input.charAt(position);
            if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') position++;
            else break;
        }
    }

    private void require(char expected) {
        if (!consume(expected)) throw error("Expected '" + expected + "'");
    }

    private boolean consume(char expected) {
        if (!atEnd() && input.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private boolean atEnd() { return position >= input.length(); }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at character " + position);
    }
}
