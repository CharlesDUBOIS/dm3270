package com.bytezone.dm3270.display;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.input.KeyCode;

import com.bytezone.dm3270.attributes.Attribute;

public class Cursor2
{
  private final Screen screen;

  private int currentPosition;
  private Field currentField;
  private boolean visible;

  private final List<Attribute> unappliedAttributes = new ArrayList<> ();

  public enum Direction
  {
    LEFT, RIGHT, UP, DOWN
  }

  public Cursor2 (Screen screen)
  {
    this.screen = screen;
  }

  public void add (Attribute attribute)
  {
    unappliedAttributes.add (attribute);
  }

  public void setChar (byte value)
  {
    ScreenPosition2 sp = screen.getScreenPosition (currentPosition);

    if (unappliedAttributes.size () > 0)
      applyAttributes (sp);

    sp.setChar (value);
  }

  public void setGraphicsChar (byte value)
  {
    ScreenPosition2 sp = screen.getScreenPosition (currentPosition);

    if (unappliedAttributes.size () > 0)
      applyAttributes (sp);

    sp.setGraphicsChar (value);
  }

  private void applyAttributes (ScreenPosition2 sp)
  {
    sp.reset ();
    for (Attribute attribute : unappliedAttributes)
      sp.addAttribute (attribute);
    unappliedAttributes.clear ();
  }

  public void move (KeyCode keyCode)
  {
    setVisible (false);

    if (keyCode == KeyCode.LEFT)
      move (Direction.LEFT);
    else if (keyCode == KeyCode.RIGHT)
      move (Direction.RIGHT);
    else if (keyCode == KeyCode.UP)
      move (Direction.UP);
    else if (keyCode == KeyCode.DOWN)
      move (Direction.DOWN);

    setVisible (true);
  }

  public void move (Direction direction)
  {
    int newPosition = -1;

    switch (direction)
    {
      case RIGHT:
        newPosition = currentPosition + 1;
        break;

      case LEFT:
        newPosition = currentPosition - 1;
        break;

      case UP:
        newPosition = currentPosition - screen.columns;
        break;

      case DOWN:
        newPosition = currentPosition + screen.columns;
        break;
    }

    moveTo (newPosition);
  }

  public void draw ()
  {
    screen.drawPosition (currentPosition, visible);
  }

  public void setVisible (boolean visible)
  {
    this.visible = visible;
    draw ();
  }

  public void moveTo (int position)
  {
    if (visible)
    {
      screen.drawPosition (currentPosition, false);
      currentPosition = screen.validate (position);
      screen.drawPosition (currentPosition, true);
    }
    else
      currentPosition = screen.validate (position);

    if (currentField != null && !currentField.contains (currentPosition))
      currentField = screen.getField (currentPosition);
  }

  public ScreenPosition2 getScreenPosition ()
  {
    return screen.getScreenPosition (currentPosition);
  }

  public Field getCurrentField ()
  {
    if (currentField == null)
      currentField = screen.getField (currentPosition);
    return currentField;
  }

  public int getLocation ()
  {
    return currentPosition;
  }
}