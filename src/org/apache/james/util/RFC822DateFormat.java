/*--- formatted by Jindent 2.0b, (www.c-lab.de/~jindent) ---*/

package org.apache.james.util;

import java.util.*;
import java.text.*;

/**
 * I suppose I could access some of the special messages within Sun's implementation of the JavaMail
 * API, but I've always been told not to do that.  This class has one static method that takes a
 * java.util.Date object and returns a nicely formatted String version of this, formatted as per the
 * RFC822 mail date format.
 * @author Serge Knystautas <sergek@lokitech.com>
 * @version 0.9
 */
public class RFC822DateFormat {
    private static DateFormat df;
    private static DecimalFormat tz;

    /**
     * SimpleDateFormat will handle most of this for us, but the timezone won't match, so we do that
     * manually
     * @return java.lang.String
     * @param d Date
     */
    public static String toString(Date d) {
        if (df == null) {
            df = new SimpleDateFormat("EE, d MMM yyyy HH:mm:ss");
        } 
        if (tz == null) {
            tz = new DecimalFormat("00");
        } 

        StringBuffer sb = new StringBuffer(df.format(d));

        sb.append(' ');

        int min = TimeZone.getDefault().getRawOffset() / 1000 / 60;

        if (min >= 0) {
            sb.append('+');
        } else {
            sb.append('-');
        }

        min = Math.abs(min);

        sb.append(tz.format(min / 60));
        sb.append(tz.format(min % 60));

        return sb.toString();
    }

}



/*--- formatting done in "Sun Java Convention" style on 07-11-1999 ---*/

