package free.yhc.feeder.model;

import java.text.SimpleDateFormat;

class DateUtils {
    private static final SimpleDateFormat dateFormats[] = new SimpleDateFormat[] {
            new SimpleDateFormat("EEE, d MMM yy HH:mm:ss z"),
            new SimpleDateFormat("EEE, d MMM yy HH:mm z"),
            new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z"),
            new SimpleDateFormat("EEE, d MMM yyyy HH:mm z"),
            new SimpleDateFormat("d MMM yy HH:mm z"),
            new SimpleDateFormat("d MMM yy HH:mm:ss z"),
            new SimpleDateFormat("d MMM yyyy HH:mm z"),
            new SimpleDateFormat("d MMM yyyy HH:mm:ss z"),
        };

    /*
    static String
    getCurrentDateString() {
        DateFormat dateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z");
        //get current date time with Date()
        Date date = new Date();
        return dateFormat.format(date);
    }
    */


}
