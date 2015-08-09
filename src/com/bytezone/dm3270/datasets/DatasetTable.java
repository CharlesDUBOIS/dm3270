package com.bytezone.dm3270.datasets;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

public class DatasetTable extends TableView<Dataset>
{
  private final ObservableList<Dataset> datasets = FXCollections.observableArrayList ();

  public DatasetTable ()
  {
    setStyle ("-fx-font-size: 11;");
    setFixedCellSize (20.0);

    TableColumn<Dataset, String> colDatasetName = new TableColumn<> ("Dataset name");
    colDatasetName.setPrefWidth (100);
    colDatasetName.setCellValueFactory (new PropertyValueFactory<> ("DatasetName"));
    getColumns ().add (colDatasetName);

    TableColumn<Dataset, Integer> colTracks = new TableColumn<> ("Tracks");
    colTracks.setPrefWidth (100);
    colTracks.setCellValueFactory (new PropertyValueFactory<> ("Tracks"));
    getColumns ().add (colTracks);

    TableColumn<Dataset, Integer> colCylinders = new TableColumn<> ("Cylinders");
    colCylinders.setPrefWidth (100);
    colCylinders.setCellValueFactory (new PropertyValueFactory<> ("Cylinders"));
    getColumns ().add (colCylinders);

    TableColumn<Dataset, Integer> colExtents = new TableColumn<> ("Extents");
    colExtents.setPrefWidth (100);
    colExtents.setCellValueFactory (new PropertyValueFactory<> ("Extents"));
    getColumns ().add (colExtents);

    TableColumn<Dataset, Integer> colPercentUsed = new TableColumn<> ("Percent used");
    colPercentUsed.setPrefWidth (100);
    colPercentUsed.setCellValueFactory (new PropertyValueFactory<> ("PercentUsed"));
    getColumns ().add (colPercentUsed);

    TableColumn<Dataset, String> colVolume = new TableColumn<> ("Volume");
    colVolume.setPrefWidth (100);
    colVolume.setCellValueFactory (new PropertyValueFactory<> ("Volume"));
    getColumns ().add (colVolume);

    setItems (datasets);
  }

  public void addDataset (Dataset dataset)
  {
    datasets.add (dataset);
  }
}