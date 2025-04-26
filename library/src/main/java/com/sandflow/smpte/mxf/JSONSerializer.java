package com.sandflow.smpte.mxf;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Set;

import org.apache.commons.numbers.fraction.Fraction;

import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

public class JSONSerializer {

  static final private Set<Class<?>> NUMBERS = Set.of(
      Byte.class,
      Short.class,
      Integer.class,
      Long.class,
      Float.class,
      Double.class,
      Boolean.class);

  static final private Set<Class<?>> PRIMITIVES = Set.of(
      UUID.class,
      AUID.class,
      UMID.class,
      Fraction.class,
      LocalDate.class,
      LocalDateTime.class,
      LocalTime.class);

  private static String escapeJSONString(String input) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      switch (c) {
        case '\"':
          sb.append("\\\"");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '\b':
          sb.append("\\b");
          break;
        case '\f':
          sb.append("\\f");
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
          if (c < 0x20 || c > 0x7F) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
      }
    }
    return sb.toString();
  }

  public static void serialize(Object obj, Writer w)
      throws IOException, IllegalArgumentException, IllegalAccessException {
    if (obj instanceof Collection) {
      w.write("[");
      boolean first = true;
      for (Object item : (Collection<?>) obj) {
        if (!first) {
          w.write(",\n");
        } else {
          first = false;
        }
        serialize(item, w);
      }
      w.write("]");
    } else if (obj.getClass().isEnum() || obj instanceof String || PRIMITIVES.contains(obj.getClass())) {
      w.write(String.format("\"%s\"", escapeJSONString(obj.toString())));
    } else if (NUMBERS.contains(obj.getClass())) {
      w.write(obj.toString());
    } else {
      w.write("{");
      boolean first = true;
      Class<?> clazz = obj.getClass();
      while (clazz != Object.class) {
        for (Field field : clazz.getDeclaredFields()) {
          if (Modifier.isFinal(field.getModifiers()))
            continue;
          field.setAccessible(true);
          Object v = field.get(obj);
          if (v == null)
            continue;
          if (!first) {
            w.write(",\n");
          } else {
            first = false;
          }
          w.write(String.format("\"%s\": ", field.getName()));
          serialize(v, w);
        }
        clazz = clazz.getSuperclass();
      }
      w.write("}");
    }
  }
}
