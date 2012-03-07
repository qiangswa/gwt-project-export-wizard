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

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IExportWizard;
import org.eclipse.ui.IWorkbench;

import com.google.gwt.eclipse.core.compile.GWTCompileSettings;
import com.myeclipsedev.gdt.eclipse.webapp.export.ExportActivator;

public class WebComponentExportWizard extends Wizard implements IExportWizard {
	private WebComponentExportPage page;

	public WebComponentExportWizard() {
		IDialogSettings workbenchSettings = ExportActivator.getDefault()
				.getDialogSettings();
		IDialogSettings section = workbenchSettings
				.getSection("ExportGWTWebAppWizard");//$NON-NLS-1$
		if (section == null) {
			section = workbenchSettings.addNewSection("ExportGWTWebAppWizard");//$NON-NLS-1$
		}
		setDialogSettings(section);
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {

	}

	@Override
	public boolean performFinish() {
		page.finish();
		IProject project = page.getProject();
		String destination = page.getDestination();

		final GWTCompileSettings compileSettings = page.getCompileSettings();

		ExportWarOp op = new ExportWarOp(project, destination, compileSettings);

		op.schedule();

		return true;

	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.wizard.Wizard#addPages()
	 */
	public void addPages() {
		page = new WebComponentExportPage("WarExportPage");
		addPage(page);
	}

}
