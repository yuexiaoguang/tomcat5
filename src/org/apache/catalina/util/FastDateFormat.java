package org.apache.catalina.util;

import java.util.Date;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;

/**
 * 快速格式化，缓存最近格式化日期信息，并使用它来避免过于频繁的调用底层格式化程序.
 * Note: 打破fieldPosition 参数格式(Date, StringBuffer, FieldPosition). 
 * 如果你关心字段的位置, 直接调用底层的DateFormat.
 */
public class FastDateFormat extends DateFormat {
    DateFormat    df;
    long          lastSec = -1;
    StringBuffer  sb      = new StringBuffer();
    FieldPosition fp      = new FieldPosition(DateFormat.MILLISECOND_FIELD);

    public FastDateFormat(DateFormat df) {
        this.df = df;
    }

    public Date parse(String text, ParsePosition pos) {
        return df.parse(text, pos);
    }

    /**
     * Note: 打破fieldPosition参数功能. Also:
     * 但还是有一个SimpleDateFormat bug, 使用"S" and "SS", 如果你想要一个毫秒字段，使用"SSS"
     */
    public StringBuffer format(Date date, StringBuffer toAppendTo,
                               FieldPosition fieldPosition) {
        long dt = date.getTime();
        long ds = dt / 1000;
        if (ds != lastSec) {
            sb.setLength(0);
            df.format(date, sb, fp);
            lastSec = ds;
        } else {
            // 显示当前毫秒到现有的字符串
            int ms = (int)(dt % 1000);
            int pos = fp.getEndIndex();
            int begin = fp.getBeginIndex();
            if (pos > 0) {
                if (pos > begin)
                    sb.setCharAt(--pos, Character.forDigit(ms % 10, 10));
                ms /= 10;
                if (pos > begin)
                    sb.setCharAt(--pos, Character.forDigit(ms % 10, 10));
                ms /= 10;
                if (pos > begin)
                    sb.setCharAt(--pos, Character.forDigit(ms % 10, 10));
            }
        }
        toAppendTo.append(sb.toString());
        return toAppendTo;
    }

    public static void main(String[] args) {
        String format = "yyyy-MM-dd HH:mm:ss.SSS";
        if (args.length > 0)
            format = args[0];
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        FastDateFormat fdf = new FastDateFormat(sdf);
        Date d = new Date();

        d.setTime(1); System.out.println(fdf.format(d) + "\t" + sdf.format(d));
        d.setTime(20); System.out.println(fdf.format(d) + "\t" + sdf.format(d));
        d.setTime(500); System.out.println(fdf.format(d) + "\t" + sdf.format(d));
        d.setTime(543); System.out.println(fdf.format(d) + "\t" + sdf.format(d));
        d.setTime(999); System.out.println(fdf.format(d) + "\t" + sdf.format(d));
        d.setTime(1050); System.out.println(fdf.format(d) + "\t" + sdf.format(d));
        d.setTime(2543); System.out.println(fdf.format(d) + "\t" + sdf.format(d));
        d.setTime(12345); System.out.println(fdf.format(d) + "\t" + sdf.format(d));
        d.setTime(12340); System.out.println(fdf.format(d) + "\t" + sdf.format(d));

        final int reps = 100000;
        {
            long start = System.currentTimeMillis();
            for (int i = 0; i < reps; i++) {
                d.setTime(System.currentTimeMillis());
                fdf.format(d);
            }
            long elap = System.currentTimeMillis() - start;
            System.out.println("fast: " + elap + " elapsed");
            System.out.println(fdf.format(d));
        }
        {
            long start = System.currentTimeMillis();
            for (int i = 0; i < reps; i++) {
                d.setTime(System.currentTimeMillis());
                sdf.format(d);
            }
            long elap = System.currentTimeMillis() - start;
            System.out.println("slow: " + elap + " elapsed");
            System.out.println(sdf.format(d));
        }
    }
}
