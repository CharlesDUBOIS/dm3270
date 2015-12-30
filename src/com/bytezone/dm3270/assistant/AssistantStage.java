package com.bytezone.dm3270.assistant;

import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

import com.bytezone.dm3270.application.ConsolePane;
import com.bytezone.dm3270.application.KeyboardStatusChangedEvent;
import com.bytezone.dm3270.application.KeyboardStatusListener;
import com.bytezone.dm3270.application.Site;
import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.display.ScreenChangeListener;
import com.bytezone.dm3270.display.ScreenWatcher;
import com.bytezone.dm3270.display.TSOCommandListener;
import com.bytezone.dm3270.filetransfer.Transfer;
import com.bytezone.dm3270.filetransfer.TransferListener;
import com.bytezone.dm3270.filetransfer.TransferManager;
import com.bytezone.dm3270.filetransfer.TransferManager.TransferStatus;
import com.bytezone.dm3270.utilities.WindowSaver;
import com.bytezone.reporter.application.ReporterNode;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class AssistantStage extends Stage implements ScreenChangeListener,
    TSOCommandListener, KeyboardStatusListener, BatchJobListener, TransferListener
{
  private final static String OS = System.getProperty ("os.name");
  private final static boolean SYSTEM_MENUBAR = OS != null && OS.startsWith ("Mac");

  private final Preferences prefs = Preferences.userNodeForPackage (this.getClass ());
  private final WindowSaver windowSaver;
  private final MenuBar menuBar;
  private final Screen screen;
  private TransferManager transferManager;
  protected Site currentSite;

  private final TSOCommand tsoCommand;
  private final Button btnHide = new Button ("Hide Window");

  private final TabPane tabPane = new TabPane ();
  private final DatasetTab datasetTab;
  private final BatchJobTab jobTab;
  private final FilesTab filesTab;
  private final TransfersTab transfersTab;
  private final CommandsTab commandsTab;
  private final List<ScreenChangeListener> screenChangeListeners;
  private final List<KeyboardStatusListener> keyboardStatusListeners;

  public AssistantStage (Screen screen, Site site)
  {
    setTitle ("File Transfers");
    this.screen = screen;

    setOnCloseRequest (e -> closeWindow ());
    btnHide.setOnAction (e -> closeWindow ());

    tsoCommand = new TSOCommand ();
    screen.getFieldManager ().addScreenChangeListener (tsoCommand);

    datasetTab = new DatasetTab (screen, site, tsoCommand);
    jobTab = new BatchJobTab (screen, site, tsoCommand);
    filesTab = new FilesTab (screen, site, tsoCommand, prefs);
    commandsTab = new CommandsTab (screen, site, tsoCommand);
    transfersTab = new TransfersTab (screen, site, tsoCommand);
    tabPane.getTabs ().addAll (datasetTab, jobTab, filesTab, commandsTab, transfersTab);
    tabPane.setTabMinWidth (80);

    screenChangeListeners =
        Arrays.asList (datasetTab, jobTab, filesTab, commandsTab, transfersTab);
    keyboardStatusListeners =
        Arrays.asList (datasetTab, jobTab, filesTab, commandsTab, transfersTab);

    datasetTab.addDatasetSelectionListener (transfersTab);
    filesTab.addFileSelectionListener (transfersTab);
    jobTab.addJobSelectionListener (transfersTab);
    //    fileTab.getCurrentFileBuffer ();

    AnchorPane anchorPane = new AnchorPane ();
    AnchorPane.setLeftAnchor (tsoCommand.getBox (), 10.0);
    AnchorPane.setBottomAnchor (tsoCommand.getBox (), 10.0);
    AnchorPane.setTopAnchor (tsoCommand.getBox (), 10.0);
    AnchorPane.setTopAnchor (btnHide, 10.0);
    AnchorPane.setBottomAnchor (btnHide, 10.0);
    AnchorPane.setRightAnchor (btnHide, 10.0);
    anchorPane.getChildren ().addAll (tsoCommand.getBox (), btnHide);

    BorderPane borderPane = new BorderPane ();
    menuBar = filesTab.getMenuBar ();
    borderPane.setTop (menuBar);
    borderPane.setCenter (tabPane);
    borderPane.setBottom (anchorPane);

    menuBar.setUseSystemMenuBar (SYSTEM_MENUBAR);

    Scene scene = new Scene (borderPane, 800, 500);// width/height
    setScene (scene);

    setOnCloseRequest (e -> closeWindow ());

    windowSaver = new WindowSaver (prefs, this, "DatasetStage");
    windowSaver.restoreWindow ();

    tabPane.getSelectionModel ().selectedItemProperty ()
        .addListener ( (obs, oldSelection, newSelection) -> select (newSelection));

    tabPane.getSelectionModel ().select (datasetTab);
  }

  public void setTransferManager (TransferManager transferManager)
  {
    this.transferManager = transferManager;
    tsoCommand.setTransferManager (transferManager);
  }

  private void select (Tab tabSelected)
  {
    if (tabSelected != null)
      ((AbstractTransferTab) tabSelected).setText ();
  }

  public void setConsolePane (ConsolePane consolePane)
  {
    tsoCommand.setConsolePane (consolePane);
  }

  public ReporterNode getReporterNode ()
  {
    return filesTab.getReporterNode ();
  }

  public void closeWindow ()
  {
    windowSaver.saveWindow ();
    hide ();
  }

  @Override
  public void batchJobSubmitted (int jobNumber, String jobName)
  {
    jobTab.batchJobSubmitted (jobNumber, jobName);
  }

  @Override
  public void batchJobEnded (int jobNumber, String jobName, String time,
      int conditionCode)
  {
    jobTab.batchJobEnded (jobNumber, jobName, time, conditionCode);
  }

  @Override
  public void batchJobFailed (int jobNumber, String jobName, String time)
  {
    jobTab.batchJobFailed (jobNumber, jobName, time);
  }

  @Override
  public void screenChanged (ScreenWatcher screenDetails)
  {
    for (ScreenChangeListener listener : screenChangeListeners)
      listener.screenChanged (screenDetails);
  }

  @Override
  public void keyboardStatusChanged (KeyboardStatusChangedEvent evt)
  {
    for (KeyboardStatusListener listener : keyboardStatusListeners)
      listener.keyboardStatusChanged (evt);
  }

  @Override
  public void tsoCommand (String command)
  {
    jobTab.tsoCommand (command);
    commandsTab.tsoCommand (command);
  }

  // ---------------------------------------------------------------------------------//
  // File transfer interface methods
  // ---------------------------------------------------------------------------------//

  @Override
  public void transferStatusChanged (TransferStatus status, Transfer transfer)
  {
    System.out.printf ("Status ......... %s%n", status);
    System.out.println (transfer);
    System.out.println ();
    if (status == TransferStatus.FINISHED && transfer.isDownloadAndIsData ())
      Platform.runLater ( () -> update (status, transfer));
  }

  private void update (TransferStatus status, Transfer transfer)
  {
    ReporterNode reporterNode = filesTab.getReporterNode ();

    if (transfer.getSiteFolderName ().isEmpty ())
    {
      //      System.out.println ("No site folder");
      reporterNode.addBuffer (transfer.getDatasetName (), transfer.combineDataBuffers ());
    }
    else
    {
      //      reporterNode.addBuffer (transfer.getDatasetName (), transfer.combineDataBuffers (),
      //                              transfer.getSiteFolderName ());
      //      System.out.println ("about to link file");
      reporterNode.addFile (transfer.getFile (), transfer.getSiteFolderName ());
    }
  }
}