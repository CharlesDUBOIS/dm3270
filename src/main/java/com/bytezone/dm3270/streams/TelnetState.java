package com.bytezone.dm3270.streams;

import com.bytezone.dm3270.display.ScreenDimensions;
import com.bytezone.dm3270.telnet.TN3270ExtendedSubcommand;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class TelnetState implements Runnable {

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter
      .ofPattern("dd MMM uuuu HH:mm:ss.S");
  private static byte[] noOp = {(byte) 0xFF, (byte) 0xF1};

  private final String[] terminalTypes =
      {"", "", "IBM-3278-2-E", "IBM-3278-3-E", "IBM-3278-4-E", "IBM-3278-5-E"};

  // preferences
  private boolean do3270Extended;
  private boolean doBinary;
  private boolean doEOR;
  private boolean doTerminalType;
  private String doDeviceType;

  // current status
  private boolean does3270Extended;
  private boolean doesEOR;
  private boolean doesBinary;
  private boolean doesTerminalType;
  private String deviceType = "";
  private List<TN3270ExtendedSubcommand.Function> functions;

  private String terminal = "";
  private TerminalServer terminalServer;
  private final boolean debug = false;

  // IO
  private AtomicLong lastAccess;
  private volatile boolean running = false;
  private Thread thread;

  private ScreenDimensions secondary = new ScreenDimensions(24, 80);

  public TelnetState() {
    setDo3270Extended(true);       // prefer extended
    setDoDeviceType(2);

    setDoEOR(true);
    setDoBinary(true);
    setDoTerminalType(true);
  }

  public void setTerminalServer(TerminalServer terminalServer) {
    this.terminalServer = terminalServer;
    thread = new Thread(this);
    thread.start();
  }

  public void setLastAccess(LocalDateTime dateTime, int bytes) {
    lastAccess.set(System.currentTimeMillis());

    if (debug) {
      System.out.printf("Read  : %,6d %s%n", bytes, FORMATTER.format(dateTime));
    }
  }

  public void write(byte[] buffer) {
    if (terminalServer != null) {
      terminalServer.write(buffer);
    }

    if (lastAccess != null) {
      lastAccess.set(System.currentTimeMillis());
    }

    if (debug) {
      System.out.printf("Write : %,6d %s%n", buffer.length,
          FORMATTER.format(LocalDateTime.now()));
    }
  }

  // This thread exists simply to keep the connection alive. It sleeps for a
  // certain period, and when it wakes it issues a NOOP if nothing else has
  // communicated with the server.

  @Override
  public void run() {
    long lastTimeIChecked;
    lastAccess = new AtomicLong(System.currentTimeMillis());
    running = true;
    long limit = 120;      // seconds to wait

    while (running) {
      try {
        lastTimeIChecked = lastAccess.get();
        long delay = (System.currentTimeMillis() - lastTimeIChecked) / 1000;
        long sleep = limit - delay;

        if (Thread.currentThread().isInterrupted()) {
          return;
        }

        if (sleep > 1) {
          Thread.sleep(sleep * 1000);
        }

        if (lastTimeIChecked == lastAccess.get()) {
          write(noOp);
        }
      } catch (InterruptedException e) {
        if (running) {
          e.printStackTrace();
        }
        return;
      }
    }
  }

  public void close() {
    if (thread != null) {
      running = false;
      thread.interrupt();
    }
  }

  public ScreenDimensions getSecondary() {
    return secondary;
  }

  // ---------------------------------------------------------------------------------//
  // Set actual (what was communicated during negotiations)
  // ---------------------------------------------------------------------------------//

  public void setDoes3270Extended(boolean state) {
    System.out.println("Does Extended        : " + state);
    does3270Extended = state;
  }

  public void setDoesEOR(boolean state) {
    System.out.println("Does EOR             : " + state);
    doesEOR = state;
  }

  public void setDoesBinary(boolean state) {
    System.out.println("Does Binary          : " + state);
    doesBinary = state;
  }

  public void setDoesTerminalType(boolean state) {
    System.out.println("Does Terminal type  : " + state);
    doesTerminalType = state;
  }

  public void setTerminal(String terminal) {
    System.out.println("Terminal            : " + terminal);
    this.terminal = terminal;
  }

  public void setDeviceType(String deviceType) {
    System.out.println("Device Type          : " + deviceType);
    this.deviceType = deviceType;

    int modelNo = 0;
    for (int i = 2; i <= 5; i++) {
      if (terminalTypes[i].equals(deviceType)) {
        modelNo = i;
        break;
      }
    }

    switch (modelNo) {
      case 2:
        secondary = new ScreenDimensions(24, 80);
        break;
      case 3:
        secondary = new ScreenDimensions(32, 80);
        break;
      case 4:
        secondary = new ScreenDimensions(43, 80);
        break;
      case 5:
        secondary = new ScreenDimensions(27, 132);
        break;
      default:
        secondary = new ScreenDimensions(24, 80);
        System.out.println("Model not found: " + deviceType);
    }
  }

  public void setFunctions(List<TN3270ExtendedSubcommand.Function> functions) {
    System.out.println("Functions            : " + functions);
    this.functions = functions;
  }

  // ---------------------------------------------------------------------------------//
  // Ask actual
  // ---------------------------------------------------------------------------------//

  public boolean does3270Extended() {
    return does3270Extended;
  }

  public boolean doesEOR() {
    return doesEOR || does3270Extended;
  }

  public boolean doesBinary() {
    return doesBinary || does3270Extended;
  }

  public boolean doesTerminalType() {
    return doesTerminalType || does3270Extended;
  }

  // ---------------------------------------------------------------------------------//
  // Ask preferences
  // ---------------------------------------------------------------------------------//

  public boolean do3270Extended() {
    return do3270Extended;
  }

  public boolean doEOR() {
    return doEOR;
  }

  public boolean doBinary() {
    return doBinary;
  }

  public boolean doTerminalType() {
    return doTerminalType;
  }

  public String doDeviceType() {
    return doDeviceType;
  }

  // ---------------------------------------------------------------------------------//
  // Set preferences
  // ---------------------------------------------------------------------------------//

  public void setDo3270Extended(boolean state) {
    do3270Extended = state;
  }

  public void setDoBinary(boolean state) {
    doBinary = state;
  }

  public void setDoEOR(boolean state) {
    doEOR = state;
  }

  public void setDoTerminalType(boolean state) {
    doTerminalType = state;
  }

  public void setDoDeviceType(int modelNo) {
    doDeviceType = terminalTypes[modelNo];
    System.out.println("setting: " + doDeviceType);
  }

  @Override
  public String toString() {
    return String.format("3270 ext ........ %s%n", does3270Extended)
        + String.format("binary .......... %s%n", doesBinary)
        + String.format("EOR ............. %s%n", doesEOR)
        + String.format("terminal type ... %s%n", doesTerminalType)
        + String.format("terminal ........ %s%n", terminal)
        + String.format("device type ..... %s%n", deviceType)
        + String.format("functions ....... %s", functions);
  }

}
