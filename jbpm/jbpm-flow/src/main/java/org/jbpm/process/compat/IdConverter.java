package org.jbpm.process.compat;

public class IdConverter {
    public static long toLongId(String s) {
        return s.hashCode();
    }
    public static String toStringId(Long l) {
        return l.toString();
    }
}
