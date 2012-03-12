package com.myeclipsedev.gdt.eclipse.command.webapp;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandlerListener;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.ui.PlatformUI;

import com.myeclipsedev.gdt.eclipse.ui.internal.wizard.WebComponentExportWizard;

public class ExportHandler extends AbstractHandler{

	@Override
	public void addHandlerListener(IHandlerListener handlerListener) {

	}

	@Override
	public Object execute(ExecutionEvent event) {

	    // Check for dirty editors and prompt to save
	    if (!PlatformUI.getWorkbench().saveAllEditors(true)) {
	      return null;
	    }
	    
		WizardDialog dialog = new WizardDialog(null, new WebComponentExportWizard());
		dialog.create();
		dialog.getShell().setText("Export");
		dialog.open();


		return null;
	}

}
