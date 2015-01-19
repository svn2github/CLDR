package org.unicode.cldr.web.gpw;

import java.util.Random;

public class GpwGenerator {
    final static String alphabet = "abcdefghijklmnopqrstuvwxyz";
    final static int alphabet_len = alphabet.length();
    final static int npw = 1;
    GpwData data;
    Random r = new Random();

    public String generate() {
        final int first = r.nextInt(8);
        final int second = r.nextInt(8-first);
        final int third = r.nextInt(3);
        return generate(first)+r.nextInt(9)+generate(second)+r.nextInt(9)+generate(third);
    }
    
    public String generate(final int pwl) {
        if (data == null) {
            data = new GpwData();
        }
        int c1, c2, c3;
        long sum = 0;
        int nchar;
        long ranno;
        int pwnum;
        double pik;
        StringBuffer password;
        Random ran = new Random(); // new random source seeded by clock

        // Pick a random starting point.
        //for (pwnum=0; pwnum < npw; pwnum++) {
            password = new StringBuffer(pwl);
            pik = ran.nextDouble(); // random number [0,1]
            ranno = (long)(pik * data.getSigma()); // weight by sum of frequencies
            sum = 0;
            for (c1=0; c1 < alphabet_len; c1++) {
                for (c2=0; c2 < alphabet_len; c2++) {
                    for (c3=0; c3 < alphabet_len; c3++) {
                        sum += data.get(c1, c2, c3);
                        if (sum > ranno) {
                            password.append(alphabet.charAt(c1));
                            password.append(alphabet.charAt(c2));
                            password.append(alphabet.charAt(c3));
                            c1 = alphabet_len; // Found start. Break all 3 loops.
                            c2 = alphabet_len;
                            c3 = alphabet_len;
                        } // if sum
                    } // for c3
                } // for c2
            } // for c1

            // Now do a random walk.
            nchar = 3;
            while (nchar < pwl) {
                c1 = alphabet.indexOf(password.charAt(nchar-2));
                c2 = alphabet.indexOf(password.charAt(nchar-1));
                sum = 0;
                for (c3=0; c3 < alphabet_len; c3++)
                    sum += data.get(c1, c2, c3);
                if (sum == 0) {
                    break;    // exit while loop
                }
                pik = ran.nextDouble();
                ranno = (long)(pik * sum);
                sum = 0;
                for (c3=0; c3 < alphabet_len; c3++) {
                    sum += data.get(c1, c2, c3);
                    if (sum > ranno) {
                        password.append(alphabet.charAt(c3));
                        c3 = alphabet_len; // break for loop
                    } // if sum
                } // for c3
                nchar ++;
            }
            return password.toString();
            // }
    }
    
    public static void main(String args[]) {
        System.out.println(new GpwGenerator().generate());
    }
}