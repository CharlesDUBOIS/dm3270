package com.bytezone.dm3270.extended;

import com.bytezone.dm3270.buffers.AbstractReplyBuffer;
import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.utilities.Dm3270Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandHeader extends AbstractReplyBuffer {

  private static final Logger LOG = LoggerFactory.getLogger(CommandHeader.class);

  private static DataType[] dataTypes =
      {DataType.TN3270_DATA, DataType.SCS_DATA, DataType.RESPONSE, DataType.BIND_IMAGE,
          DataType.UNBIND, DataType.NVT_DATA, DataType.REQUEST, DataType.SSCP_LU_DATA,
          DataType.PRINT_EOJ, DataType.BID};

  private static final byte ERR_COND_CLEARED = 0x00;

  private static final byte RQ_NO_RESPONSE = 0x00;
  private static final byte RQ_ERROR_RESPONSE = 0x01;
  private static final byte RQ_ALWAYS_RESPONSE = 0x02;

  private static final byte POSITIVE_RESPONSE = 0x00;
  private static final byte NEGATIVE_RESPONSE = 0x01;

  private final DataType dataType;
  private ResponseType responseType;
  private RequestType requestType;
  private final int commandSeq;

  public enum DataType {
    TN3270_DATA, SCS_DATA, RESPONSE, BIND_IMAGE, UNBIND, NVT_DATA, REQUEST, SSCP_LU_DATA,
    PRINT_EOJ,
    // https://tools.ietf.org/html/draft-ietf-tn3270e-extensions-00#section-4.5.3
    BID
  }

  private enum RequestType {
    ERR_COND_CLEARED
  }

  private enum ResponseType {
    NO_RESPONSE, ERROR_RESPONSE, ALWAYS_RESPONSE, POSITIVE_RESPONSE, NEGATIVE_RESPONSE
  }

  public CommandHeader(byte[] buffer) {
    this(buffer, 0, buffer.length);
    assert buffer.length == 5;
  }

  public CommandHeader(byte[] buffer, int offset, int length) {
    super(buffer, offset, length);

    dataType = dataTypes[data[0]];

    switch (dataType) {
      case TN3270_DATA:
      case SCS_DATA:
      case BID:
        switch (data[2]) {
          case RQ_NO_RESPONSE:
            responseType = ResponseType.NO_RESPONSE;
            break;
          case RQ_ERROR_RESPONSE:
            responseType = ResponseType.ERROR_RESPONSE;
            break;
          case RQ_ALWAYS_RESPONSE:
            responseType = ResponseType.ALWAYS_RESPONSE;
            break;
          default:
            throw new UnsupportedOperationException("Unsupported response type " + data[2]);
        }
        break;

      case RESPONSE:
        switch (data[2]) {
          case POSITIVE_RESPONSE:
            responseType = ResponseType.POSITIVE_RESPONSE;
            break;
          case NEGATIVE_RESPONSE:
            responseType = ResponseType.NEGATIVE_RESPONSE;
            break;
          default:
            throw new UnsupportedOperationException("Unsupported response type " + data[2]);
        }
        break;

      case BIND_IMAGE:
        break;

      case UNBIND:
        break;

      case NVT_DATA:
        break;

      case REQUEST:
        if (data[2] == ERR_COND_CLEARED) {
          requestType = RequestType.ERR_COND_CLEARED;
        } else {
          LOG.warn("Unknown request data: {}", String.format("%02X", data[2]));
        }
        break;

      case SSCP_LU_DATA:
        break;

      case PRINT_EOJ:
        break;

      default:
        throw new UnsupportedOperationException("Unsupported data type " + dataType);
    }

    commandSeq = Dm3270Utility.unsignedShort(data, 3);
  }

  public DataType getDataType() {
    return dataType;
  }

  @Override
  public void process(Screen screen) {
    if (responseType == ResponseType.ALWAYS_RESPONSE) {
      byte[] header = new byte[5];
      header[0] = 0x02;
      Dm3270Utility.packUnsignedShort(commandSeq, header, 3);
      CommandHeader commandHeader = new CommandHeader(header);
      byte[] value = {0x00};

      setReply(new ResponseCommand(commandHeader, value, 0, value.length));
    }
  }

  // A CommandHeader needs to expand possible 0xFF bytes, but it does not
  // have EOR bytes on the end.
  @Override
  public byte[] getTelnetData() {
    int length = data.length + countFF(data);
    if (length == data.length) {
      return data;
    }

    byte[] returnBuffer = new byte[length];
    copyAndExpand(data, returnBuffer);
    return returnBuffer;
  }

  @Override
  public String toString() {
    String requestText = requestType == null ? "" : requestType.toString();
    String responseText = responseType == null ? "" : responseType.toString();

    return String.format("HDR: %04d, %-12s, %s, %s", commandSeq, dataType, requestText,
        responseText);
  }

}
