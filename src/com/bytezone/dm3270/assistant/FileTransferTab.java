package com.bytezone.dm3270.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import com.bytezone.dm3270.display.Field;
import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.display.ScreenChangeListener;
import com.bytezone.dm3270.display.ScreenDetails;
import com.bytezone.dm3270.filetransfer.FileTransferOutboundSF;
import com.bytezone.dm3270.filetransfer.Transfer;
import com.bytezone.reporter.application.NodeSelectionListener;
import com.bytezone.reporter.application.ReporterNode;
import com.bytezone.reporter.application.TreePanel;
import com.bytezone.reporter.application.TreePanel.FileNode;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;

public class FileTransferTab extends TransferTab
    implements ScreenChangeListener, NodeSelectionListener
{
  private final List<Transfer> transfers = new ArrayList<> ();
  private Transfer currentTransfer;

  private final Screen screen;
  private final ReporterNode reporterNode;
  private final BorderPane borderPane = new BorderPane ();

  private boolean isTSOCommandScreen;
  private Field tsoCommandField;
  private FileNode currentFileNode;

  public FileTransferTab (Screen screen, TextField text, Button execute,
      Preferences prefs)
  {
    super ("Transfers", text, execute);

    this.screen = screen;

    reporterNode = new ReporterNode (prefs);
    borderPane.setCenter (reporterNode.getRootNode ());
    reporterNode.getTreePanel ().addNodeSelectionListener (this);
    reporterNode.getTreePanel ().getTree ().requestFocus ();
    currentFileNode = reporterNode.getSelectedNode ();

    setContent (borderPane);
  }

  @Override
  public void nodeSelected (FileNode fileNode)
  {
    currentFileNode = fileNode;
    setText ();
  }

  public void addTransfer (Transfer transfer)
  {
    if (transfer.isSendData ())
    {
      transfers.add (transfer);
      Platform.runLater ( () -> addBuffer (transfer));
    }
  }

  private void addBuffer (Transfer transfer)
  {
    if (reporterNode == null)
      return;

    TreePanel treePanel = reporterNode.getTreePanel ();
    treePanel.addBuffer (transfer.getFileName (), transfer.combineDataBuffers ());
  }

  public byte[] getCurrentFileBuffer ()
  {
    FileNode fileNode = reporterNode.getSelectedNode ();
    if (fileNode == null)
    {
      System.out.println ("No file selected to transfer");
      return null;
    }
    else
    {
      System.out.println ("File to transfer: " + fileNode);
      return fileNode.getBuffer ();
    }
  }

  // called from FileTransferOutboundSF.processOpen()
  // Save the current transfer so it is available to subsequent FileTransferOutboundSF
  // commands.
  public void openTransfer (Transfer transfer)
  {
    currentTransfer = transfer;
  }

  public Transfer getTransfer (FileTransferOutboundSF transferRecord)
  {
    currentTransfer.add (transferRecord);
    return currentTransfer;
  }

  public Transfer closeTransfer (FileTransferOutboundSF transferRecord)
  {
    if (currentTransfer == null)
    {
      System.out.println ("Null current transfer");
      return null;
    }

    Transfer transfer = currentTransfer;
    currentTransfer.add (transferRecord);

    addTransfer (currentTransfer);// add to the file tree
    currentTransfer = null;

    return transfer;
  }

  public void closeTransfer ()
  {
    currentTransfer = null;
  }

  @Override
  public void screenChanged ()
  {
    ScreenDetails screenDetails = screen.getScreenDetails ();
    isTSOCommandScreen = screenDetails.isTSOCommandScreen ();
    tsoCommandField = screenDetails.getTSOCommandField ();

    if (isSelected ())
      setText ();
  }

  @Override
      void setText ()
  {
    if (currentFileNode == null)
    {
      txtCommand.setText ("");
      btnExecute.setDisable (true);
      return;
    }
    //    if (reporterNode == null)
    //    {
    //      txtCommand.setText ("");
    //      btnExecute.setDisable (true);
    //      return;
    //    }

    //    String report = selectedBatchJob.getOutputFile ();
    //    String command = report == null ? selectedBatchJob.outputCommand ()
    //        : String.format ("IND$FILE GET %s", report);
    String command = "IND$FILE PUT " + currentFileNode;

    if (!isTSOCommandScreen)
      command = "TSO " + command;

    txtCommand.setText (command);

    btnExecute.setDisable (tsoCommandField == null || command.isEmpty ());
  }
}