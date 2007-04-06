// TODO: fix this. currently disabled.

//
//  MailSender.java
//  cldrtools
//
//  Created by Steven R. Loomis on 3/24/2005.
//  Copyright 2005 IBM. All rights reserved.
//
//  Rewritten to use JavaMail  http://java.sun.com/products/javamail/

package org.unicode.cldr.web;

import java.util.*;
import javax.mail.*;
import java.io.*;

import javax.mail.internet.*;


/**
 * Helper class. Sends mail with a simple interface */
public class MailSender {
    /**
     * Intrnal function - write something to the log
     */
    public static synchronized void log(String to, String what, Throwable t) {
        try{ 
          OutputStream file = new FileOutputStream("cldrmail.log", true); // Append
          PrintWriter pw = new PrintWriter(file);
          
          pw.println (new Date().toString() + ": " + to + " : " + what);
          if(t != null) {
            pw.println(t.toString());
            t.printStackTrace(pw);
          }
          
          pw.close();
          file.close();
        } catch(IOException ioe) {
            System.err.println("MailSender::log:  " +ioe.toString() + " whilst processing " +  to + " - " + what);
        }
    }

    /** 
     * Footer to be placed at the bottom of emails
     */
    public static final String footer =             
                "\n----------\n"+
                "This email was generated automatically as part of the CLDR survey process\n"+
                "http://www.unicode.org/cldr\n"+                
                "If you have any questions about it,\nplease contact your organization's CLDR Technical Committee member,\nor: surveytool@unicode.org\n";
                
/*    public static void sendMail(String to, String subject, String body) {
        String from = System.getProperty("CLDR_FROM");
        String smtp = System.getProperty("CLDR_SMTP");
        sendMail(smtp,from,to,subject,body);
    }
    */
    /**
     * Send a piece of mail
     * @param smtp smtp server
     * @param from From: user
     * @param to to: user
     * @param subject mail subject
     * @param body mail body
     */
    public static synchronized void sendMail(String smtp, String mailFromAddress, String mailFromName, String from, String to, String subject, String body) {
        if(to.equals("admin@")) {
            log(to,"Suppressed: " + subject,null);        
//            System.err.println("Not emailing admin.");
            return;
        }
        try {
            Properties env = System.getProperties();
            env.put("mail.host", smtp);
            MimeMessage ourMessage = new MimeMessage(Session.getInstance(env, null));
            ourMessage.setFrom(new InternetAddress(from, "CLDR Survey Tool"));
            ourMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            if(mailFromAddress != null) {
                Address replyTo[] = { new InternetAddress(mailFromAddress, mailFromName) };
                ourMessage.setReplyTo(replyTo);
            }
            ourMessage.setSubject(subject);
            ourMessage.setText(body+footer);
            Transport.send(ourMessage);
            log(to,subject,null);
        } catch(Throwable t) {  
            System.err.println("MAIL ERR: for" + to + ", " + t.toString() + " - check cldrmail.log");
            //t.printStackTrace();
            log(to,"FAIL: "+subject,t);
        }
    }
    /*
    public static synchronized void sendMail(String smtp, String from, Address[] bcc, String subject, String body) {
    try {
            Properties env = System.getProperties();
            env.put("mail.host", smtp);
            MimeMessage ourMessage = new MimeMessage(Session.getInstance(env, null));
            ourMessage.setFrom(new InternetAddress(from));
            ourMessage.setRecipients(Message.RecipientType.BCC, bcc);
            ourMessage.setSubject(subject);
            ourMessage.setText(body+footer);
            Transport.send(ourMessage);
        } catch(javax.mail.MessagingException t) {  
            System.err.println("Err while mailing: " + t.toString());
            t.printStackTrace();
        }
    }
    */
}
