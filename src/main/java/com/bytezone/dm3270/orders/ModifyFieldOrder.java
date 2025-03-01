package com.bytezone.dm3270.orders;

import com.bytezone.dm3270.attributes.Attribute;
import com.bytezone.dm3270.display.DisplayScreen;

import java.util.Optional;

public class ModifyFieldOrder extends Order {

  public ModifyFieldOrder(byte[] buffer, int offset) {
    assert buffer[offset] == Order.MODIFY_FIELD;

    int totalAttributePairs = buffer[offset + 1] & 0xFF;

    this.buffer = new byte[totalAttributePairs * 2 + 2];
    this.buffer[0] = buffer[offset];
    this.buffer[1] = buffer[offset + 1];

    int ptr = offset + 2;
    int bptr = 2;
    for (int i = 0; i < totalAttributePairs; i++) {
      Optional<Attribute> attribute =
          Attribute.getAttribute(buffer[ptr], buffer[ptr + 1]);
      assert attribute.isPresent();
      this.buffer[bptr++] = buffer[ptr++];
      this.buffer[bptr++] = buffer[ptr++];
    }
  }

  @Override
  public void process(DisplayScreen screen) {
  }

}
