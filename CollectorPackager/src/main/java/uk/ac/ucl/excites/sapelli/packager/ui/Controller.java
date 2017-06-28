/**
 * Sapelli data collection platform: http://sapelli.org
 * <p>
 * Copyright 2012-2016 University College London - ExCiteS group
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.ucl.excites.sapelli.packager.ui;


import java.io.File;
import java.util.List;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ucl.excites.sapelli.collector.model.Project;
import uk.ac.ucl.excites.sapelli.packager.sapelli.ProjectChecker;
import uk.ac.ucl.excites.sapelli.packager.sapelli.ProjectUtils;
import uk.ac.ucl.excites.sapelli.packager.sapelli.ProjectZipper;
import uk.ac.ucl.excites.sapelli.packager.ui.UiMessageManager.State;
import uk.ac.ucl.excites.sapelli.shared.io.FileHelpers;

@Slf4j
public class Controller
{

	// Privates:
	private ProjectChecker projectChecker;
	private UiMessageManager uiMessageManager = new UiMessageManager();

	//----- UI

	//--- Main elements
	@FXML
	public AnchorPane root;

	//--- Working directory elements
	@FXML
	public AnchorPane workingDirectoryBackground;
	@FXML
	private Label workingDirectoryLabel;

	//--- Warnings/messages elements
	@FXML
	public Label warningLabel;
	@FXML
	public Label messageLabel;

	//--- Packager
	@FXML
	private Button packageButton;


	/**
	 * Method to be called when the Browse button is clicked
	 *
	 * @param actionEvent {@link ActionEvent}
	 */
	public void onBrowseButtonClicked(ActionEvent actionEvent)
	{
		// Get the Directory Chooser
		final Stage stage = (Stage) root.getScene().getWindow();
		final DirectoryChooser directoryChooser = new DirectoryChooser();
		if(projectChecker != null && projectChecker.sapelliProjectDirExists())
			directoryChooser.setInitialDirectory(projectChecker.getSapelliProjectDir());
		directoryChooser.setTitle("Select directory of project");
		final File sapelliProjectDir = directoryChooser.showDialog(stage);

		// Ensure that the user has selected something
		if(sapelliProjectDir == null)
			return;

		// Create a new Project Checker
		projectChecker = new ProjectChecker(sapelliProjectDir);

		// Validate Working directory and Project
		if(validateWorkingDirectory())
			validateProject();
	}

	/**
	 * Method to be called when the Refresh button is clicked
	 *
	 * @param actionEvent {@link ActionEvent}
	 */
	public void onRefreshButtonClicked(ActionEvent actionEvent)
	{
		if(projectChecker == null)
			return;

		projectChecker.refresh();

		// Clear UI
		uiMessageManager.clear();

		// Update UI
		validateProject();
	}

	/**
	 * Method to be called when the Package button is clicked
	 *
	 * @param actionEvent {@link ActionEvent}
	 */
	public void onPackageButtonClicked(ActionEvent actionEvent)
	{
		// Reset the uiMessageManager
		uiMessageManager.clear();
		// Let's assume that this will be a success:
		uiMessageManager.setState(State.SUCCESS);

		try
		{
			// Create a ProjectZipper:
			ProjectZipper projectZipper = new ProjectZipper(projectChecker);

			// Check if file already exists:
			File previousFile = projectZipper.getSapFile();
			if(previousFile.exists())
			{
				// Create the backup
				String backupFileName = FileHelpers.trimFileExtensionAndDot(previousFile.toString());
				backupFileName += "-" + System.currentTimeMillis() + "." + ProjectZipper.SAP_EXTENSION;

				File backupFile = new File(backupFileName);

				if(previousFile.renameTo(backupFile))
				{
					uiMessageManager.setState(State.WARNING);
					uiMessageManager.addMessages("The was already a Sapelli file in the directory. We made a backup of it on: " + backupFile.getName());
				}
				else
				{
					uiMessageManager.setState(State.ERROR);
					uiMessageManager.addMessages("The is already a Sapelli file in the directory, but we cannot make a backup of it.");
				}
			}

			// Zip the project
			projectZipper.zipProject();

			uiMessageManager.addMessages("The project has been packaged into " + projectZipper.getSapFile());

			// Log
			log.info(uiMessageManager.getMessages());

		}
		catch(Exception e)
		{
			uiMessageManager.setState(State.ERROR);
			uiMessageManager.addMessages("Error while trying to package the project: \n" + e.getLocalizedMessage());

			log.error("Error while Zipping project", e);
		}

		// Update the UI
		updateUI();
	}

	/**
	 * Validate the Working Directory and update the UI
	 *
	 * @return true if we have a valid directory
	 */
	private boolean validateWorkingDirectory()
	{
		// 1: Update Working Dir Or return
		if(projectChecker.sapelliProjectDirExists())
		{
			// Set style
			workingDirectoryBackground.getStyleClass().clear();
			workingDirectoryBackground.getStyleClass().addAll("box-with-padding", "working-dir-success");

			// Set text
			workingDirectoryLabel.setText(projectChecker.getSapelliProjectDir().toString());
		}
		else
		{
			// Set style
			workingDirectoryBackground.getStyleClass().clear();
			workingDirectoryBackground.getStyleClass().addAll("box-with-padding", "working-dir-error");

			// Set text and exit
			workingDirectoryLabel.setText("Please select a directory...");
			return false;
		}

		return projectChecker.sapelliProjectDirExists();
	}

	/**
	 * Validate the Project and update the UI
	 */
	private void validateProject()
	{
		// Reset the uiMessageManager
		uiMessageManager.clear();

		// 2: Check if PROJECT.XML exists
		if(projectChecker.projectXmlExists())
		{
			// Get Project, Warnings and Errors
			final Project project = projectChecker.getProject();
			final List<String> warnings = projectChecker.getWarnings();
			final List<String> errors = projectChecker.getErrors();
			final List<String> missing = projectChecker.getMissingFiles();

			// CASE 1: SUCCESS
			// Project exists, no warnings, no errors, no missing files
			if(project != null && warnings.isEmpty() && errors.isEmpty() && missing.isEmpty())
			{
				uiMessageManager.setState(State.SUCCESS);
				uiMessageManager.addMessages(ProjectUtils.printProjectInfo(project));

				// Enable the Package button
				packageButton.setDisable(false);
			}
			// CASE 2: WARNINGS
			// Project exists with warnings or missing files, no errors
			if(project != null && (!warnings.isEmpty() || !missing.isEmpty()) && errors.isEmpty())
			{
				// Disable the Package button
				packageButton.setDisable(true);

				uiMessageManager.setState(State.WARNING);
				// TODO: Fix this
				uiMessageManager.addMessages(warnings.toString() + "\n\n" + missing.toString());
			}

			// CASE 3: ERROR
			// Project does not exists or there are errors
			if(project == null || !errors.isEmpty())
			{
				// Disable the Package button
				packageButton.setDisable(true);

				uiMessageManager.setState(State.ERROR);
				// TODO: Fix this
				uiMessageManager.addMessages(errors.toString());
			}

			// TODO: 02/06/2017 Continue this?
		}
		else
		{
			// Inform the user that the PROJECT.XML does not exist
			uiMessageManager.setState(State.ERROR);
			// Set text to inform user
			uiMessageManager.addMessages("The directory '" + projectChecker.getSapelliProjectDir() + "' does not contain a PROJECT.xml and therefore it is not a valid Sapelli project. \n\nMake sure you have a file named PROJECT.xml in this directory.");

			// Disable the Package button
			packageButton.setDisable(true);
		}


		// TODO: 01/06/2017 Continue the validation here


		// Finally, update the UI
		updateUI();
	}

	/**
	 * Update UI
	 */
	private void updateUI()
	{
		// Update UI
		switch(uiMessageManager.getState())
		{
			case DEFAULT:
				warningLabel.getStyleClass().clear();
				warningLabel.getStyleClass().add("warning-default");
				messageLabel.setText(uiMessageManager.getMessages());
				break;
			case SUCCESS:
				warningLabel.getStyleClass().clear();
				warningLabel.getStyleClass().add("warning-success");
				messageLabel.setText(uiMessageManager.getMessages());
				break;
			case WARNING:
				warningLabel.getStyleClass().clear();
				warningLabel.getStyleClass().add("warning-warning");
				messageLabel.setText(uiMessageManager.getMessages());
				break;
			case ERROR:
				warningLabel.getStyleClass().clear();
				warningLabel.getStyleClass().add("warning-error");
				messageLabel.setText(uiMessageManager.getMessages());
				break;
		}
	}


}