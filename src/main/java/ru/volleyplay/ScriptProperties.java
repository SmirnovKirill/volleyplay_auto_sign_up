package ru.volleyplay;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Properties;

public record ScriptProperties(String imapHost, int imapPort, ZoneId mailServerTimeZone, String username, String password) {
  private static final String PROPERTY_IMAP_HOST = "imapHost";
  private static final String PROPERTY_IMAP_PORT = "imapPort";
  private static final String PROPERTY_MAIL_SERVER_TIME_ZONE = "mailServerTimeZone";
  private static final String PROPERTY_USERNAME = "username";
  private static final String PROPERTY_PASSWORD = "password";

  public static ScriptProperties readProperties() throws IOException {
    Properties properties = new Properties();
    properties.load(AutoSignUp.class.getResourceAsStream("/script.properties"));

    return new ScriptProperties(
        properties.getProperty(PROPERTY_IMAP_HOST),
        Integer.parseInt(properties.getProperty(PROPERTY_IMAP_PORT)),
        ZoneId.of(properties.getProperty(PROPERTY_MAIL_SERVER_TIME_ZONE)),
        properties.getProperty(PROPERTY_USERNAME),
        properties.getProperty(PROPERTY_PASSWORD)
    );
  }
}
