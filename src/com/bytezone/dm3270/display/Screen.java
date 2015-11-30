package com.bytezone.dm3270.display;

import static com.bytezone.dm3270.application.Console.Function.TERMINAL;
import static com.bytezone.dm3270.commands.AIDCommand.NO_AID_SPECIFIED;

import java.awt.Toolkit;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.prefs.Preferences;

import com.bytezone.dm3270.application.Console.Function;
import com.bytezone.dm3270.application.ConsolePane;
import com.bytezone.dm3270.application.KeyboardStatusChangedEvent;
import com.bytezone.dm3270.application.KeyboardStatusListener;
import com.bytezone.dm3270.application.Site;
import com.bytezone.dm3270.assistant.AssistantStage;
import com.bytezone.dm3270.attributes.Attribute;
import com.bytezone.dm3270.attributes.ColorAttribute;
import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.commands.WriteControlCharacter;
import com.bytezone.dm3270.plugins.PluginsStage;
import com.bytezone.dm3270.structuredfields.SetReplyModeSF;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.stage.Stage;

public class Screen extends Canvas implements DisplayScreen
{
  private final static Toolkit defaultToolkit = Toolkit.getDefaultToolkit ();
  private final static boolean SHOW_CURSOR = true;
  private final static boolean HIDE_CURSOR = false;

  private static final byte[] saveScreenReplyTypes =
      { Attribute.XA_HIGHLIGHTING, Attribute.XA_FGCOLOR, Attribute.XA_CHARSET,
        Attribute.XA_BGCOLOR, Attribute.XA_TRANSPARENCY };

  private final ScreenPacker screenPacker;
  private final Function function;

  private final ScreenPosition[] screenPositions;
  private final FieldManager fieldManager;
  private final FontManager fontManager;

  private final PluginsStage pluginsStage;
  private final AssistantStage assistantStage;
  private ConsolePane consolePane;

  private final GraphicsContext gc;
  private FontData fontData;

  private final Pen pen;
  private final Cursor cursor = new Cursor (this);

  private final int xOffset = 4;              // padding left and right
  private final int yOffset = 4;              // padding top and bottom

  public final int rows;
  public final int columns;
  public final int screenSize;

  private byte currentAID;
  private byte replyMode;
  private byte[] replyTypes = new byte[0];

  private int insertedCursorPosition = -1;
  private boolean keyboardLocked;
  private boolean insertMode;
  private boolean readModifiedAll = false;

  private final HistoryManager historyManager = new HistoryManager ();

  public enum BuildInstruction
  {
    BUILD_FIELDS, DONT_BUILD_FIELDS
  }

  public Screen (int rows, int columns, Preferences prefs, Function function,
      PluginsStage pluginsStage, Site site)
  {
    this.rows = rows;
    this.columns = columns;
    screenSize = rows * columns;
    this.function = function;

    gc = getGraphicsContext2D ();

    fontManager = new FontManager (this, prefs);
    fieldManager = new FieldManager (this);
    assistantStage = new AssistantStage (this, site);

    screenPositions = new ScreenPosition[screenSize];
    pen = new PenType1 (screenPositions);

    screenPacker = new ScreenPacker (pen, fieldManager);
    screenPacker.addTSOCommandListener (assistantStage);
    addKeyboardStatusChangeListener (assistantStage);

    this.pluginsStage = pluginsStage;
    pluginsStage.setScreen (this);

    fieldManager.addScreenChangeListener (assistantStage);
    fieldManager.addScreenChangeListener (screenPacker);
  }

  public void setStatusText (String text)
  {
    consolePane.setStatusText (text);
  }

  // called from the ConsolePane constructor
  public void setConsolePane (ConsolePane consolePane)
  {
    this.consolePane = consolePane;
    assistantStage.setConsolePane (consolePane);
    addKeyboardStatusChangeListener (consolePane);
  }

  public FieldManager getFieldManager ()
  {
    return fieldManager;
  }

  public boolean isTSOCommandScreen ()
  {
    return fieldManager.getScreenDetails ().isTSOCommandScreen ();
  }

  public Field getTSOCommandField ()
  {
    return fieldManager.getScreenDetails ().getTSOCommandField ();
  }

  public String getPrefix ()
  {
    return fieldManager.getScreenDetails ().getPrefix ();
  }

  public FontManager getFontManager ()
  {
    return fontManager;
  }

  // called by WriteCommand.process()
  public PluginsStage getPluginsStage ()
  {
    return pluginsStage;
  }

  public AssistantStage getAssistantStage ()
  {
    return assistantStage;
  }

  public void closeAssistantStage ()
  {
    assistantStage.closeWindow ();
  }

  public Function getFunction ()
  {
    return function;
  }

  public String getPreviousTSOCommand ()
  {
    return screenPacker.getPreviousTSOCommand ();
  }

  // called from AIDCommand.process()
  public void addTSOCommand (String command)
  {
    screenPacker.addTSOCommand (command);
  }

  public void listTSOCommands ()
  {
    screenPacker.listTSOCommands ();
  }

  // display a message on the screen - only used when logging off
  public void displayText (String text)
  {
    gc.setFill (ColorAttribute.colors[8]);                // black
    gc.fillRect (0, 0, getWidth (), getHeight ());
    gc.setFill (ColorAttribute.colors[5]);                // turquoise

    int x = 120;
    int y = 100;
    int height = 20;

    for (String line : text.split ("\n"))
    {
      gc.fillText (line, x, y);
      y += height;
    }
  }

  // called from AIDCommand.process()
  // called from ConsolePane constructor
  // called from PluginsStage.processPluginAuto()
  // called from PluginsStage.processPluginRequest()
  // called from PluginsStage.processReply()
  public Cursor getScreenCursor ()
  {
    return cursor;
  }

  // called from WriteControlCharacter.process()
  public void resetInsertMode ()
  {
    if (insertMode)
      toggleInsertMode ();
  }

  // called from ConsoleKeyPress.handle()
  // called from ConsolePane.sendAID()
  public void toggleInsertMode ()
  {
    insertMode = !insertMode;
    fireKeyboardStatusChange ("");
  }

  // called from Cursor.typeChar()
  // called from ConsolePane.sendAID()
  public boolean isInsertMode ()
  {
    return insertMode;
  }

  // called from EraseAllUnprotectedCommand.process()
  public void eraseAllUnprotected ()
  {
    Optional<Field> firstUnprotectedField = fieldManager.eraseAllUnprotected ();

    restoreKeyboard ();         // resets the AID to NO_AID_SPECIFIED
    resetModified ();
    draw ();

    if (firstUnprotectedField.isPresent ())
      cursor.moveTo (firstUnprotectedField.get ().getFirstLocation ());
  }

  public void buildFields (WriteControlCharacter wcc)
  {
    fieldManager.buildFields ();              // what about resetModified?
  }

  // called from WriteCommand.process()
  public void checkRecording ()
  {
    byte savedReplyMode = replyMode;
    byte[] savedReplyTypes = replyTypes;

    setReplyMode (SetReplyModeSF.RM_CHARACTER, saveScreenReplyTypes);
    historyManager.saveScreen (readBuffer ());

    setReplyMode (savedReplyMode, savedReplyTypes);
  }

  // called from FontManager.selectFont()
  void resize ()
  {
    ((Stage) getScene ().getWindow ()).sizeToScene ();
    eraseScreen ();
    draw ();
  }

  // called from this.eraseAllUnprotected()
  // called from this.resize()
  // called from Write.process()
  public void draw ()
  {
    int pos = 0;
    int charHeight = fontData.getHeight ();
    int charWidth = fontData.getWidth ();
    int ascent = fontData.getAscent ();
    int descent = fontData.getDescent ();

    for (int row = 0; row < rows; row++)
      for (int col = 0; col < columns; col++)
        screenPositions[pos++].draw (gc, getX (col, charWidth), getY (row, charHeight),
                                     HIDE_CURSOR, charWidth, charHeight, ascent, descent);

    if (insertedCursorPosition >= 0)
    {
      cursor.moveTo (insertedCursorPosition);
      insertedCursorPosition = -1;
      cursor.setVisible (true);
    }

    pos = cursor.getLocation ();
    int row = pos / columns;
    int col = pos % columns;
    screenPositions[pos].draw (gc, getX (col, charWidth), getY (row, charHeight),
                               SHOW_CURSOR, charWidth, charHeight, ascent, descent);
  }

  // called from Field.draw()
  // called from Cursor.moveTo() - when moving the cursor around the screen
  // called from Cursor.setVisible()
  // called from Cursor.backspace()
  // called from Cursor.delete()
  // called from Cursor.eraseEOL()
  // called from Cursor.moveTo()
  void drawPosition (int position, boolean hasCursor)
  {
    int row = position / columns;
    int col = position % columns;

    int charWidth = fontData.getWidth ();
    int charHeight = fontData.getHeight ();
    int ascent = fontData.getAscent ();
    int descent = fontData.getDescent ();

    screenPositions[position].draw (gc, getX (col, charWidth), getY (row, charHeight),
                                    hasCursor, charWidth, charHeight, ascent, descent);
  }

  private int getX (int col, int charWidth)
  {
    return xOffset + col * charWidth;
  }

  private int getY (int row, int charHeight)
  {
    return yOffset + row * charHeight;
  }

  // called from FontManager.setFont()
  void characterSizeChanged (FontData fontData)
  {
    this.fontData = fontData;
    setWidth (fontData.getWidth () * columns + xOffset * 2);
    setHeight (fontData.getHeight () * rows + yOffset * 2);

    gc.setFont (fontData.getFont ());
    if (consolePane != null)
      consolePane.setFontData (fontData);
  }

  void eraseScreen ()
  {
    gc.setFill (ColorAttribute.colors[8]);             // black
    gc.fillRect (0, 0, getWidth (), getHeight ());
  }

  // called from Cursor.home()
  Optional<Field> getHomeField ()
  {
    List<Field> fields = fieldManager.getUnprotectedFields ();
    if (fields != null && fields.size () > 0)
      return Optional.of (fields.get (0));
    return Optional.empty ();
  }

  public void setAID (byte aid)
  {
    currentAID = aid;
  }

  public byte getAID ()
  {
    return currentAID;
  }

  // called from SetReplyModeSF
  // called from this.checkRecording()
  public void setReplyMode (byte replyMode, byte[] replyTypes)
  {
    this.replyMode = replyMode;
    this.replyTypes = replyTypes;
  }

  public void setFieldText (Field field, String text)
  {
    try
    {
      field.setText (text.getBytes ("CP1047"));
      field.setModified (true);
      field.draw ();                      // draws the field without a cursor
    }
    catch (UnsupportedEncodingException e)
    {
      e.printStackTrace ();
    }
  }

  public String getScreenText ()
  {
    StringBuilder text = new StringBuilder ();

    text.append (pen.getScreenText (80));
    text.append ("\n");
    text.append (fieldManager.getTotalsText ());

    return text.toString ();
  }

  // ---------------------------------------------------------------------------------//
  // DisplayScreen interface methods
  // ---------------------------------------------------------------------------------//

  @Override
  public Pen getPen ()
  {
    return pen;
  }

  @Override
  public ScreenPosition getScreenPosition (int position)
  {
    return screenPositions[position];
  }

  @Override
  public ScreenPosition[] getScreenPositions ()
  {
    return screenPositions;
  }

  @Override
  public int validate (int position)
  {
    return pen.validate (position);
  }

  @Override
  public void clearScreen ()
  {
    eraseScreen ();
    cursor.moveTo (0);
    pen.clearScreen ();
    fieldManager.reset ();
  }

  @Override
  public void insertCursor (int position)
  {
    insertedCursorPosition = position;                // move it here later
  }

  // ---------------------------------------------------------------------------------//
  // Convert screen contents to an AID command
  // ---------------------------------------------------------------------------------//

  // called from ConsoleKeyPress.handle() in response to a user command
  // called from this.readModifiedFields(0x..) below
  public AIDCommand readModifiedFields ()
  {
    return screenPacker.readModifiedFields (currentAID, getScreenCursor ().getLocation (),
                                            readModifiedAll);
  }

  // Called from:
  //      ReadCommand.process() in response to a ReadBuffer (F2) command
  //      ReadPartitionSF.process() in response to a ReadBuffer (F2) command
  //      ScreenHistory.requestScreen()
  public AIDCommand readBuffer ()
  {
    return screenPacker.readBuffer (currentAID, getScreenCursor ().getLocation (),
                                    replyMode, replyTypes);
  }

  // Called from ReadCommand.process() in response to a ReadModified (F6)
  // or a ReadModifiedAll (6E) command
  // Called from ReadPartitionSF.process() in response to a ReadModified (F6)
  // or a ReadModifiedAll (6E) command
  public AIDCommand readModifiedFields (byte type)
  {
    switch (type)
    {
      case Command.READ_MODIFIED_F6:
        return readModifiedFields ();

      case Command.READ_MODIFIED_ALL_6E:
        readModifiedAll = true;
        AIDCommand command = readModifiedFields ();
        readModifiedAll = false;
        return command;

      default:
        System.out.println ("Unknown type in Screen.readModifiedFields()");
    }

    return null;
  }

  // ---------------------------------------------------------------------------------//
  // Events to be processed from WriteControlCharacter.process()
  // ---------------------------------------------------------------------------------//

  public void resetPartition ()
  {
  }

  public void startPrinter ()
  {
  }

  public void soundAlarm ()
  {
    defaultToolkit.beep ();
  }

  public void restoreKeyboard ()
  {
    setAID (NO_AID_SPECIFIED);
    cursor.setVisible (true);
    keyboardLocked = false;
    fireKeyboardStatusChange ("");
  }

  public void lockKeyboard (String keyName)
  {
    keyboardLocked = true;
    fireKeyboardStatusChange (keyName);

    if (function == TERMINAL)
      cursor.setVisible (false);
  }

  public void resetModified ()
  {
    fieldManager.getUnprotectedFields ().forEach (f -> f.setModified (false));
  }

  public boolean isKeyboardLocked ()
  {
    return keyboardLocked;
  }

  // ---------------------------------------------------------------------------------//
  // Listener events
  // ---------------------------------------------------------------------------------//

  private final Set<KeyboardStatusListener> keyboardChangeListeners = new HashSet<> ();

  private void fireKeyboardStatusChange (String keyName)
  {
    KeyboardStatusChangedEvent evt =
        new KeyboardStatusChangedEvent (insertMode, keyboardLocked, keyName);
    keyboardChangeListeners.forEach (l -> l.keyboardStatusChanged (evt));
  }

  public void addKeyboardStatusChangeListener (KeyboardStatusListener listener)
  {
    if (!keyboardChangeListeners.contains (listener))
      keyboardChangeListeners.add (listener);
  }

  public void removeKeyboardStatusChangeListener (KeyboardStatusListener listener)
  {
    if (keyboardChangeListeners.contains (listener))
      keyboardChangeListeners.remove (listener);
  }

  // ---------------------------------------------------------------------------------//
  // Divide the ScreenPositions into fields
  // ---------------------------------------------------------------------------------//

  //  static List<List<ScreenPosition>> divide (ScreenPosition[] screenPositions)
  //  {
  //    List<List<ScreenPosition>> components = new ArrayList<> ();
  //    List<ScreenPosition> positions = new ArrayList<ScreenPosition> ();
  //
  //    int start = -1;
  //    int first = -1;
  //    int ptr = 0;
  //
  //    while (ptr != first)                    // not wrapped around to the first field yet
  //    {
  //      ScreenPosition screenPosition = screenPositions[ptr];
  //
  //      if (screenPosition.isStartField ())   // check for the start of a new field
  //      {
  //        if (start >= 0)                     // if there is a field to add
  //        {
  //          components.add (new ArrayList<> (positions));
  //          positions.clear ();
  //        }
  //        else
  //          first = ptr;                      // this is the first field on the screen
  //
  //        start = ptr;                        // beginning of the current field
  //      }
  //
  //      if (start >= 0)                       // if we are in a field...
  //        positions.add (screenPosition);     // collect next field's positions
  //
  //      // increment ptr and wrap around
  //      if (++ptr == screenPositions.length)  // faster than validate()
  //      {
  //        ptr = 0;
  //        if (first == -1)
  //          break;                            // wrapped around and still no fields
  //      }
  //    }
  //
  //    if (start >= 0 && positions.size () > 0)
  //      components.add (new ArrayList<> (positions));
  //
  //    return components;
  //  }

  // ---------------------------------------------------------------------------------//
  // Process a field's ScreenPositions
  // ---------------------------------------------------------------------------------//

  //  static void setContexts (List<ScreenPosition> positions)
  //  {
  //    StartFieldAttribute startFieldAttribute = positions.get (0).getStartFieldAttribute ();
  //    ScreenContext defaultContext = startFieldAttribute.process (null, null);
  //
  //    if (startFieldAttribute.isExtended ())
  //      setExtendedContext (defaultContext, positions);
  //    else
  //      for (ScreenPosition screenPosition : positions)
  //        screenPosition.setScreenContext (defaultContext);
  //
  //    if (startFieldAttribute.isHidden ())
  //      positions.forEach (sp -> sp.setVisible (false));
  //  }
  //
  //  private static void setExtendedContext (ScreenContext defaultContext,
  //      List<ScreenPosition> positions)
  //  {
  //    boolean first = true;
  //    ScreenContext currentContext = defaultContext;
  //
  //    for (ScreenPosition screenPosition : positions)
  //    {
  //      for (Attribute attribute : screenPosition.getAttributes ())
  //        currentContext = attribute.process (defaultContext, currentContext);
  //
  //      if (first)
  //      {
  //        first = false;
  //        defaultContext = currentContext;
  //      }
  //      screenPosition.setScreenContext (currentContext);
  //    }
  //  }

  // ---------------------------------------------------------------------------------//
  // Screen history
  // ---------------------------------------------------------------------------------//

  public Optional<HistoryManager> pause ()             // triggered by cmd-s
  {
    if (historyManager.size () == 0)
      return Optional.empty ();

    historyManager.pause (keyboardLocked);
    keyboardLocked = true;

    return Optional.of (historyManager);
  }

  public void resume ()                     // also triggered by cmd-s
  {
    keyboardLocked = historyManager.resume ();
  }
}