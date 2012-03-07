/*******************************************************************************
 * Copyright (c) 2012  Q.S Wang (qiangswa@google.com).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Q.S Wang (qiangswa@google.com) - initial API and implementation
 *******************************************************************************/
package com.myeclipsedev.ui.internal.wizard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

import com.google.gdt.eclipse.core.ActiveProjectFinder;
import com.google.gdt.eclipse.core.StatusUtilities;
import com.google.gdt.eclipse.core.extensions.ExtensionQuery;
import com.google.gwt.eclipse.core.GWTPlugin;
import com.google.gwt.eclipse.core.GWTPluginLog;
import com.google.gwt.eclipse.core.compile.GWTCompileSettings;
import com.google.gwt.eclipse.core.compile.ui.GWTCompileDialog.GWTCompileProjectValidator;
import com.google.gwt.eclipse.core.launch.GWTLaunchAttributes;
import com.google.gwt.eclipse.core.launch.ui.EntryPointModulesSelectionBlock;
import com.google.gwt.eclipse.core.launch.ui.EntryPointModulesSelectionBlock.IModulesChangeListener;
import com.google.gwt.eclipse.core.nature.GWTNature;
import com.google.gwt.eclipse.core.properties.GWTProjectProperties;
import com.google.gwt.eclipse.core.runtime.GWTRuntime;
import com.google.gwt.eclipse.core.runtime.GwtCapabilityChecker;
import com.myeclipsedev.gdt.eclipse.webapp.export.ExportActivator;

public class WebComponentExportPage extends WizardPage {
	public static boolean isWindows = SWT.getPlatform().toLowerCase()
			.startsWith("win"); //$NON-NLS-1$

	private Combo destinationNameCombo;
	private Button destinationBrowseButton;
	private Text projectText;
	private Button chooseProjectButton;
	private IProject project;
	private ComboViewer logLevelComboViewer;
	private ComboViewer outputStyleComboViewer;
	private String outputStyle;
	private String logLevel;
	private final FieldListener listener = new FieldListener();

	private EntryPointModulesSelectionBlock entryPointModulesBlock;

	protected WebComponentExportPage(String pageName) {
		super(pageName);
		setImageDescriptor(ExportActivator.imageDescriptorFromPlugin(
				ExportActivator.PLUGIN_ID,
				"icons/full/ctool16/webfragment_wizban.gif"));
		setTitle("War export");
		setMessage("Export GWT Web project to the local file system.");
	}

	@Override
	public void createControl(Composite parent) {
		project = ActiveProjectFinder.getInstance().getProject();

		Composite composite = new Composite(parent, SWT.NULL);
		GridLayout layout = new GridLayout(1, false);
		composite.setLayout(layout);

		createSourceAndDestinationGroup(composite);
		createCompilerAndShellComponent(composite);

		createEntryPointModulesComponent(composite);

		Dialog.applyDialogFont(parent);
		setControl(composite);

		addEventHandlers();

		initializeControls();
	}

	private void createCompilerAndShellComponent(Composite parent) {
		Group group = new Group(parent, SWT.None);
		group.setText("Compiler && Shell");

		group.setLayout(new GridLayout(2, false));
		group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		createLogLevelControl(group);
		createOutputStyleControl(group);
	}

	private void createLogLevelControl(Composite parent) {
		new Label(parent, SWT.None).setText("Log level:");

		logLevelComboViewer = new ComboViewer(parent, SWT.READ_ONLY);
		logLevelComboViewer.setContentProvider(new ArrayContentProvider());
		logLevelComboViewer.setLabelProvider(new DefaultComboLabelProvider());
		logLevelComboViewer.setInput(GWTLaunchAttributes.LOG_LEVELS);
	}

	private void createOutputStyleControl(Composite parent) {
		new Label(parent, SWT.None).setText("Output style:");

		outputStyleComboViewer = new ComboViewer(parent, SWT.READ_ONLY);
		outputStyleComboViewer.setContentProvider(new ArrayContentProvider());
		outputStyleComboViewer
				.setLabelProvider(new DefaultComboLabelProvider());
		outputStyleComboViewer.setInput(GWTLaunchAttributes.OUTPUT_STYLES);
	}

	private void initializeControls() {
		// Set the project field if we have one set
		if (project != null) {
			projectText.setText(project.getName());
		}

		// If we have a GWT project, get its saved compilation settings;
		// otherwise
		// just use the defaults settings.
		GWTCompileSettings settings = (project != null) ? GWTProjectProperties
				.getGwtCompileSettings(project) : new GWTCompileSettings();

		initializeLogLevel(settings.getLogLevel());
		initializeOutputStyle(settings.getOutputStyle());
	}

	public GWTCompileSettings getCompileSettings() {
		GWTCompileSettings settings = new GWTCompileSettings(project);
		settings.setOutputStyle(outputStyle);
		settings.setLogLevel(logLevel);
		return settings;
	}

	private void initializeLogLevel(String level) {
		logLevelComboViewer.setSelection(new StructuredSelection(level));
	}

	private void initializeOutputStyle(String style) {
		outputStyleComboViewer.setSelection(new StructuredSelection(style));
	}

	/**
	 * @param composite
	 */
	private void createSourceAndDestinationGroup(Composite parent) {

		Composite composite = new Composite(parent, SWT.NULL);
		GridLayout layout = new GridLayout(3, false);
		composite.setLayout(layout);
		GridData data = new GridData(GridData.FILL_HORIZONTAL);
		composite.setLayoutData(data);
		createExportComponentGroup(composite);
		createDestinationGroup(composite);

	}

	/**
	 * Creates the export source resource specification widgets.
	 * 
	 * @param parent
	 *            a <code>Composite</code> that is to be used as the parent of
	 *            this group's collection of visual components
	 * @see org.eclipse.swt.widgets.Composite
	 */
	protected void createExportComponentGroup(Composite parent) {

		// Project label
		Label projectLabel = new Label(parent, SWT.NONE);
		projectLabel.setText("Project name:");

		projectText = new Text(parent, SWT.BORDER);
		projectText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		chooseProjectButton = new Button(parent, SWT.NONE);
		chooseProjectButton.setText("Browse...");

	}

	private void addEventHandlers() {
		projectText.addModifyListener(listener);

		destinationNameCombo.addModifyListener(listener);

		chooseProjectButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				IJavaProject selectedProject = chooseProject();
				if (selectedProject != null) {
					projectText.setText(selectedProject.getElementName());
				}
			}
		});
	}

	private IJavaProject chooseProject() {
		IJavaProject[] javaProjects;

		try {
			javaProjects = JavaCore.create(
					ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects();
		} catch (JavaModelException e) {
			ExportActivator.logError(e);
			javaProjects = new IJavaProject[0];
		}

		// Filter the list to only show GWT projects
		List<IJavaProject> gwtProjects = new ArrayList<IJavaProject>();
		for (IJavaProject javaProject : javaProjects) {
			if (GWTNature.isGWTProject(javaProject.getProject())) {
				gwtProjects.add(javaProject);
			}
		}

		ILabelProvider labelProvider = new JavaElementLabelProvider(
				JavaElementLabelProvider.SHOW_DEFAULT);
		ElementListSelectionDialog dialog = new ElementListSelectionDialog(
				getShell(), labelProvider);
		dialog.setTitle("Project Selection");
		dialog.setMessage("Choose a project to compile");
		dialog.setElements(gwtProjects.toArray(new IJavaProject[0]));
		dialog.setInitialSelections(new Object[] { JavaCore.create(project) });

		dialog.setHelpAvailable(false);
		if (dialog.open() == Window.OK) {
			return (IJavaProject) dialog.getFirstResult();
		}
		return null;
	}

	/**
	 * @return
	 */

	protected void createDestinationGroup(
			org.eclipse.swt.widgets.Composite parent) {

		// Destination label
		Label destinationLabel = new Label(parent, SWT.NONE);
		destinationLabel.setText("Destination");
		// destination name combo field
		destinationNameCombo = new Combo(parent, SWT.SINGLE | SWT.BORDER);
		destinationNameCombo.setLayoutData(new GridData(
				GridData.FILL_HORIZONTAL));

		// destination browse button
		destinationBrowseButton = new Button(parent, SWT.PUSH);
		destinationBrowseButton.setText("Browse...");
		destinationBrowseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleDestinationBrowseButtonPressed();
			}
		});
		destinationBrowseButton.setEnabled(true);

	}

	/**
	 *  
	 */
	protected void handleDestinationBrowseButtonPressed() {

		FileDialog dialog = new FileDialog(destinationNameCombo.getShell(),
				SWT.SAVE);
		String fileName = projectText.getText();
		String[] filters = new String[] { "*.war" };

		if (!isWindows) {
			if (filters.length != 0 && filters[0] != null
					&& filters[0].indexOf('.') != -1) {
				fileName += filters[0].substring(filters[0].indexOf('.'));
			}
		}
		dialog.setFileName(fileName);
		if (isWindows) {
			dialog.setFilterExtensions(filters);
		}
		String filename = dialog.open();
		if (filename != null)
			destinationNameCombo.setText(filename);
	}

	public IProject getProject() {
		return project;
	}

	public String getDestination() {
		return destinationNameCombo.getText();
	}

	private void createEntryPointModulesComponent(Composite parent) {
		Group group = new Group(parent, SWT.BORDER);
		group.setText("Entry Point Modules");

		group.setLayout(new GridLayout(3, false));

		group.setLayoutData(new GridData(GridData.FILL_BOTH));

		GridLayout groupLayout = (GridLayout) group.getLayout();
		groupLayout.marginBottom = 8;
		group.setLayout(groupLayout);

		entryPointModulesBlock = new EntryPointModulesSelectionBlock(null,
				listener);
		entryPointModulesBlock.doFillIntoGrid(group, 3);
	}

	private void fieldChanged() {
		IStatus status = updateFields();

		boolean valid = (status.getSeverity() != IStatus.ERROR);
		setPageComplete(valid);
	}

	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if (visible) {
			restoreWidgetsValues();
			fieldChanged();
		}
	}

	private void restoreWidgetsValues() {
		IDialogSettings settings = getDialogSettings();
		if (settings != null) {
			String[] sourceNames = settings.getArray("GWT_EXPORT_PROJECT_NAME");
			if (sourceNames == null)
				return; // ie.- no settings stored
			for (int i = 0; i < sourceNames.length; i++) {
				if (sourceNames[i] == null)
					sourceNames[i] = ""; //$NON-NLS-1$
			}
			destinationNameCombo.setItems(sourceNames);
		}

	}

	private IStatus updateFields() {
		IStatus projectStatus = updateProjectAndCompileSettings();
		IStatus destinationStatus = updateDestination();
		IStatus logLevelStatus = updateLogLevel();
		IStatus outputStyleStatus = updateOutputStyle();
		IStatus entryPointModulesStatus = updateEntryPointModules();

		return updateStatus(new IStatus[] { projectStatus, destinationStatus,
				logLevelStatus, outputStyleStatus, entryPointModulesStatus });
	}

	private IStatus updateDestination() {
		String destination = destinationNameCombo.getText();
		String ext = new Path(destination).getFileExtension();
		if (ext == null || !ext.equals("war")) {
			return new Status(IStatus.ERROR, ExportActivator.PLUGIN_ID,
					"The target file must have the .war extension");
		}

		return Status.OK_STATUS;
	}

	private IStatus updateLogLevel() {
		logLevel = (String) ((StructuredSelection) logLevelComboViewer
				.getSelection()).getFirstElement();
		return StatusUtilities.OK_STATUS;
	}

	private IStatus updateOutputStyle() {
		outputStyle = (String) ((StructuredSelection) outputStyleComboViewer
				.getSelection()).getFirstElement();
		return StatusUtilities.OK_STATUS;
	}

	private IStatus updateProjectAndCompileSettings() {
		project = null;

		String projectName = projectText.getText().trim();
		if (projectName.length() == 0) {
			return StatusUtilities.newErrorStatus("Enter the project name",
					GWTPlugin.PLUGIN_ID);
		}

		IProject enteredProject = ResourcesPlugin.getWorkspace().getRoot()
				.getProject(projectName);
		if (!enteredProject.exists()) {
			return StatusUtilities.newErrorStatus("Project does not exist",
					GWTPlugin.PLUGIN_ID);
		}

		if (!enteredProject.isOpen()) {
			return StatusUtilities.newErrorStatus("Project is not open",
					GWTPlugin.PLUGIN_ID);
		}

		if (!GWTNature.isGWTProject(enteredProject)) {
			return StatusUtilities.newErrorStatus(projectName
					+ " is not a GWT project", GWTPlugin.PLUGIN_ID);
		}

		String validViaExtensionMsg = validateProjectViaExtensions(enteredProject);
		if (validViaExtensionMsg != null) {
			return StatusUtilities.newErrorStatus(validViaExtensionMsg,
					GWTPlugin.PLUGIN_ID);
		}

		project = enteredProject;

		try {
			if (IMarker.SEVERITY_ERROR == enteredProject
					.findMaxProblemSeverity(IMarker.PROBLEM, true,
							IResource.DEPTH_INFINITE)) {
				return StatusUtilities.newWarningStatus(
						"The project {0} has errors.", GWTPlugin.PLUGIN_ID,
						enteredProject.getName());
			}
		} catch (CoreException e) {
			GWTPluginLog.logError(e);
		}

		return StatusUtilities.OK_STATUS;
	}

	private String validateProjectViaExtensions(IProject project) {

		ExtensionQuery<GWTCompileProjectValidator> extQuery = new ExtensionQuery<GWTCompileProjectValidator>(
				GWTPlugin.PLUGIN_ID, "gwtCompileProjectValidator", "class");
		List<ExtensionQuery.Data<GWTCompileProjectValidator>> enablementFinders = extQuery
				.getData();
		for (ExtensionQuery.Data<GWTCompileProjectValidator> enablementFinder : enablementFinders) {
			String validityString = enablementFinder.getExtensionPointData()
					.validate(project);
			if (validityString != null) {
				return validityString;
			}
		}

		return null;
	}

	private IStatus updateStatus(IStatus status) {
		if (status.getSeverity() == IStatus.OK) {
			status = StatusUtilities.newOkStatus(
					"Build the project with the GWT compiler",
					GWTPlugin.PLUGIN_ID);
		}

		this.setMessage(status.getMessage(), convertSeverity(status));

		return status;
	}

	private static int convertSeverity(IStatus status) {
		switch (status.getSeverity()) {
		case IStatus.ERROR:
			return IMessageProvider.ERROR;
		case IStatus.WARNING:
			return IMessageProvider.WARNING;
		case IStatus.INFO:
			return IMessageProvider.INFORMATION;
		default:
			return IMessageProvider.NONE;
		}
	}

	private IStatus updateStatus(IStatus[] status) {
		return updateStatus(StatusUtilities
				.getMostImportantStatusWithMessage(status));
	}

	private IStatus updateEntryPointModules() {
		updateEntryPointModulesIfProjectChanged();

		if (entryPointModulesBlock.getModules().isEmpty()) {
			return StatusUtilities.newErrorStatus("Add an entry point module",
					GWTPlugin.PLUGIN_ID);
		}

		if (!areMultipleModulesAllowed()
				&& entryPointModulesBlock.getModules().size() > 1) {
			return StatusUtilities
					.newErrorStatus(
							"Projects using GWT 1.5 or lower may only specify one entry point module to compile",
							GWTPlugin.PLUGIN_ID);
		}

		return StatusUtilities.OK_STATUS;
	}

	private boolean areMultipleModulesAllowed() {
		try {
			IJavaProject javaProject = JavaCore.create(project);
			if (javaProject != null) {
				GWTRuntime sdk = GWTRuntime.findSdkFor(javaProject);
				if (sdk != null) {
					return new GwtCapabilityChecker(sdk)
							.doesCompilerAllowMultipleModules();
				}
			}
		} catch (JavaModelException e) {
			GWTPluginLog.logError(e);
		}
		return false;
	}

	private void updateEntryPointModulesIfProjectChanged() {
		if (project != null) {
			IJavaProject javaProject = JavaCore.create(project);
			if (javaProject != null
					&& !javaProject.equals(entryPointModulesBlock
							.getJavaProject())) {
				// Set the project for the block (needed for adding a module)
				entryPointModulesBlock.setJavaProject(javaProject);

				GWTCompileSettings settings = GWTProjectProperties
						.getGwtCompileSettings(project);

				// Set the default and initially-selected modules for the block
				// from
				// the saved settings
				entryPointModulesBlock.setDefaultModules(settings
						.getEntryPointModules());
				entryPointModulesBlock.setModules(settings
						.getEntryPointModules());
			}
		} else {
			entryPointModulesBlock.setJavaProject(null);
			entryPointModulesBlock.setDefaultModules(Collections
					.<String> emptyList());
			entryPointModulesBlock.setModules(Collections.<String> emptyList());
		}
	}

	/**
	 * Provides the labels for the log level and output style combos.
	 * 
	 */
	private static class DefaultComboLabelProvider extends LabelProvider {
		@Override
		public String getText(Object element) {
			String element2 = (String) element;
			return element2.toUpperCase().charAt(0)
					+ element2.toLowerCase().substring(1);
		}
	}

	private class FieldListener implements ModifyListener,
			ISelectionChangedListener, IModulesChangeListener {
		public void modifyText(ModifyEvent e) {
			fieldChanged();
		}

		public void onModulesChanged() {
			fieldChanged();
		}

		public void selectionChanged(SelectionChangedEvent event) {
			fieldChanged();
		}
	}

	public void finish() {
		saveWidgetValues();

	}

	private void saveWidgetValues() {
		IDialogSettings settings = getDialogSettings();
		if (settings != null) {
			// update source names history
			String[] sourceNames = settings.getArray("GWT_EXPORT_PROJECT_NAME");
			if (sourceNames == null) {
				sourceNames = new String[0];
			}

			String newName = destinationNameCombo.getText();

			// rip out any empty filenames and trim length to 5
			ArrayList<String> newNames = new ArrayList<String>();
			for (int i = 0; i < sourceNames.length && i < 5; i++) {
				if (sourceNames[i].trim().length() > 0
						&& !newName.equals(sourceNames[i])) {
					newNames.add(sourceNames[i]);
				}
			}
			newNames.add(0, destinationNameCombo.getText());
			sourceNames = new String[newNames.size()];
			newNames.toArray(sourceNames);

			settings.put("GWT_EXPORT_PROJECT_NAME", sourceNames);
		}
	}

}
