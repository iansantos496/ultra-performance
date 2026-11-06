package com.ultraperformance.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitor e escritor JSON mínimo (objetos, listas, textos, números, booleanos e null).
 * Existe para manter o núcleo sem dependências externas.
 */
public final class MiniJson {
	private final String s;
	private int pos;

	private MiniJson(String s) {
		this.s = s;
	}

	public static Object parse(String text) {
		MiniJson p = new MiniJson(text);
		p.skipWs();
		Object v = p.readValue();
		p.skipWs();
		if (p.pos != p.s.length()) {
			throw new IllegalArgumentException("Texto extra após o JSON na posição " + p.pos);
		}
		return v;
	}

	private void skipWs() {
		while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
			pos++;
		}
	}

	private char peek() {
		if (pos >= s.length()) {
			throw new IllegalArgumentException("Fim inesperado do JSON");
		}
		return s.charAt(pos);
	}

	private void expect(char c) {
		if (peek() != c) {
			throw new IllegalArgumentException("Esperado '" + c + "' na posição " + pos + " mas veio '" + peek() + "'");
		}
		pos++;
	}

	private Object readValue() {
		char c = peek();
		if (c == '{') {
			return readObject();
		}
		if (c == '[') {
			return readArray();
		}
		if (c == '"') {
			return readString();
		}
		if (s.startsWith("true", pos)) {
			pos += 4;
			return Boolean.TRUE;
		}
		if (s.startsWith("false", pos)) {
			pos += 5;
			return Boolean.FALSE;
		}
		if (s.startsWith("null", pos)) {
			pos += 4;
			return null;
		}
		return readNumber();
	}

	private Map<String, Object> readObject() {
		Map<String, Object> m = new LinkedHashMap<String, Object>();
		expect('{');
		skipWs();
		if (peek() == '}') {
			pos++;
			return m;
		}
		while (true) {
			skipWs();
			String k = readString();
			skipWs();
			expect(':');
			skipWs();
			m.put(k, readValue());
			skipWs();
			char c = peek();
			pos++;
			if (c == '}') {
				return m;
			}
			if (c != ',') {
				throw new IllegalArgumentException("Esperado ',' ou '}' na posição " + (pos - 1));
			}
		}
	}

	private List<Object> readArray() {
		List<Object> l = new ArrayList<Object>();
		expect('[');
		skipWs();
		if (peek() == ']') {
			pos++;
			return l;
		}
		while (true) {
			skipWs();
			l.add(readValue());
			skipWs();
			char c = peek();
			pos++;
			if (c == ']') {
				return l;
			}
			if (c != ',') {
				throw new IllegalArgumentException("Esperado ',' ou ']' na posição " + (pos - 1));
			}
		}
	}

	private String readString() {
		expect('"');
		StringBuilder sb = new StringBuilder();
		while (true) {
			char c = peek();
			pos++;
			if (c == '"') {
				return sb.toString();
			}
			if (c != '\\') {
				sb.append(c);
				continue;
			}
			char e = peek();
			pos++;
			switch (e) {
				case '"':
					sb.append('"');
					break;
				case '\\':
					sb.append('\\');
					break;
				case '/':
					sb.append('/');
					break;
				case 'b':
					sb.append('\b');
					break;
				case 'f':
					sb.append('\f');
					break;
				case 'n':
					sb.append('\n');
					break;
				case 'r':
					sb.append('\r');
					break;
				case 't':
					sb.append('\t');
					break;
				case 'u':
					if (pos + 4 > s.length()) {
						throw new IllegalArgumentException("Escape \\u incompleto");
					}
					sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
					pos += 4;
					break;
				default:
					throw new IllegalArgumentException("Escape inválido \\" + e);
			}
		}
	}

	private Object readNumber() {
		int start = pos;
		boolean floating = false;
		while (pos < s.length()) {
			char c = s.charAt(pos);
			if (c == '.' || c == 'e' || c == 'E') {
				floating = true;
			} else if (!(c == '-' || c == '+' || (c >= '0' && c <= '9'))) {
				break;
			}
			pos++;
		}
		if (start == pos) {
			throw new IllegalArgumentException("Valor inesperado na posição " + pos);
		}
		String n = s.substring(start, pos);
		return floating ? (Object) Double.valueOf(n) : (Object) Long.valueOf(n);
	}

	// ---- escrita ----

	public static String write(Object value) {
		StringBuilder sb = new StringBuilder();
		writeValue(sb, value, 0);
		sb.append('\n');
		return sb.toString();
	}

	private static void indent(StringBuilder sb, int level) {
		for (int i = 0; i < level; i++) {
			sb.append("  ");
		}
	}

	private static void writeValue(StringBuilder sb, Object v, int level) {
		if (v == null) {
			sb.append("null");
		} else if (v instanceof Map) {
			Map<?, ?> m = (Map<?, ?>) v;
			if (m.isEmpty()) {
				sb.append("{}");
				return;
			}
			sb.append("{\n");
			int i = 0;
			for (Map.Entry<?, ?> e : m.entrySet()) {
				indent(sb, level + 1);
				writeString(sb, String.valueOf(e.getKey()));
				sb.append(": ");
				writeValue(sb, e.getValue(), level + 1);
				sb.append(++i < m.size() ? ",\n" : "\n");
			}
			indent(sb, level);
			sb.append('}');
		} else if (v instanceof List) {
			List<?> l = (List<?>) v;
			if (l.isEmpty()) {
				sb.append("[]");
				return;
			}
			sb.append("[\n");
			for (int i = 0; i < l.size(); i++) {
				indent(sb, level + 1);
				writeValue(sb, l.get(i), level + 1);
				sb.append(i + 1 < l.size() ? ",\n" : "\n");
			}
			indent(sb, level);
			sb.append(']');
		} else if (v instanceof String) {
			writeString(sb, (String) v);
		} else if (v instanceof Boolean) {
			sb.append(v.toString());
		} else if (v instanceof Double || v instanceof Float) {
			double d = ((Number) v).doubleValue();
			sb.append(Double.isNaN(d) || Double.isInfinite(d) ? "0" : Double.toString(d));
		} else if (v instanceof Number) {
			sb.append(((Number) v).longValue());
		} else {
			writeString(sb, String.valueOf(v));
		}
	}

	private static void writeString(StringBuilder sb, String str) {
		sb.append('"');
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			switch (c) {
				case '"':
					sb.append("\\\"");
					break;
				case '\\':
					sb.append("\\\\");
					break;
				case '\n':
					sb.append("\\n");
					break;
				case '\r':
					sb.append("\\r");
					break;
				case '\t':
					sb.append("\\t");
					break;
				default:
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
			}
		}
		sb.append('"');
	}
}
