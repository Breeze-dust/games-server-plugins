package com.dontdoit.criteria;

/** 数值范围：支持 "a..b"、"..b"、"a.."、".."，两端都是闭区间；反转 = 取补集。 */
public final class NumRange {

    public static final NumRange ANY = new NumRange(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

    public final double min;
    public final double max;

    private NumRange(double min, double max) {
        this.min = min;
        this.max = max;
    }

    public static NumRange parse(String text) {
        if (text == null) {
            return ANY;
        }
        String s = text.trim();
        int idx = s.indexOf("..");
        if (idx < 0) {
            try {
                double v = Double.parseDouble(s);
                return new NumRange(v, v);
            } catch (NumberFormatException e) {
                return ANY;
            }
        }
        String a = s.substring(0, idx).trim();
        String b = s.substring(idx + 2).trim();
        double min = Double.NEGATIVE_INFINITY;
        double max = Double.POSITIVE_INFINITY;
        try {
            if (!a.isEmpty()) {
                min = Double.parseDouble(a);
            }
        } catch (NumberFormatException ignored) {
            min = Double.NEGATIVE_INFINITY;
        }
        try {
            if (!b.isEmpty()) {
                max = Double.parseDouble(b);
            }
        } catch (NumberFormatException ignored) {
            max = Double.POSITIVE_INFINITY;
        }
        return new NumRange(min, max);
    }

    public boolean contains(double v) {
        return v >= min && v <= max;
    }

    /** 反转：范围外的值。 */
    public boolean containsReversed(double v) {
        return !contains(v);
    }

    @Override
    public String toString() {
        return min + ".." + max;
    }
}
