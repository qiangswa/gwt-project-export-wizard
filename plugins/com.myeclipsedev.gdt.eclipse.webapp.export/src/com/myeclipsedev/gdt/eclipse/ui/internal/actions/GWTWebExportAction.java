package com.myeclipsedev.gdt.eclipse.ui.internal.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IActionDelegate;

import com.myeclipsedev.gdt.eclipse.command.webapp.ExportHandler;

public class GWTWebExportAction implements IActionDelegate {

	public GWTWebExportAction() {

	}

	@Override
	public void run(IAction action) {
		new ExportHandler().execute(null);
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {

	}

}
