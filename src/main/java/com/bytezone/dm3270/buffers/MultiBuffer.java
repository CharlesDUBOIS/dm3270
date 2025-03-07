package com.bytezone.dm3270.buffers;

import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.utilities.Dm3270Utility;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiBuffer implements Buffer {

  private static final Logger LOG = LoggerFactory.getLogger(MultiBuffer.class);

  private List<Buffer> buffers = new ArrayList<>();

  public void addBuffer(Buffer buffer) {
    buffers.add(buffer);
  }

  @Override
  public byte[] getData() {
    byte[] data = new byte[size()];
    int ptr = 0;
    for (Buffer buffer : buffers) {
      LOG.debug(Dm3270Utility.toHex(buffer.getData()));
      System.arraycopy(buffer.getData(), 0, data, ptr, buffer.size());
      ptr += buffer.size();
    }
    LOG.debug(Dm3270Utility.toHex(data));
    return data;
  }

  @Override
  public byte[] getTelnetData() {
    List<byte[]> telnets = new ArrayList<>();

    int size = 0;
    for (Buffer buffer : buffers) {
      byte[] telnet = buffer.getTelnetData();
      telnets.add(telnet);
      size += telnet.length;
    }

    byte[] returnBuffer = new byte[size];
    int ptr = 0;
    for (byte[] buffer : telnets) {
      System.arraycopy(buffer, 0, returnBuffer, ptr, buffer.length);
      ptr += buffer.length;
    }

    return returnBuffer;
  }

  @Override
  public int size() {
    int size = 0;
    for (Buffer buffer : buffers) {
      size += buffer.size();
    }
    return size;
  }

  @Override
  public void process(Screen screen) {
    for (Buffer buffer : buffers) {
      buffer.process(screen);
    }
  }

  @Override
  public String toString() {
    StringBuilder text = new StringBuilder();

    for (Buffer buffer : buffers) {
      text.append(buffer.toString());
      text.append("\n\n");
    }

    if (text.length() > 0) {
      text.deleteCharAt(text.length() - 1);
      text.deleteCharAt(text.length() - 1);
    }

    return text.toString();
  }

}
