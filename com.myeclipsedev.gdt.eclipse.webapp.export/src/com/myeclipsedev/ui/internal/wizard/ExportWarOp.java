/*******************************************************************************
 * Copyright (c) 2012  Q.S Wang (qiangswa@google.com).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Q.S Wang (qiangswa@google.com) - initial API and implementation
 *     The GWT compiling logic is borrowed from google gdt.
 *******************************************************************************/
package com.myeclipsedev.ui.internal.wizard;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.osgi.util.NLS;
import org.osgi.service.prefs.BackingStoreException;

import com.google.gdt.eclipse.core.WebAppUtilities;
import com.google.gdt.eclipse.core.console.CustomMessageConsole;
import com.google.gdt.eclipse.core.console.MessageConsoleUtilities;
import com.google.gdt.eclipse.core.console.TerminateProcessAction;
import com.google.gwt.eclipse.core.compile.GWTCompileRunner;
import com.google.gwt.eclipse.core.compile.GWTCompileSettings;
import com.google.gwt.eclipse.core.properties.GWTProjectProperties;
import com.myeclipsedev.gdt.eclipse.webapp.export.ExportActivator;
import com.myeclipsedev.ui.internal.utils.ArchiveSaveFailureException;
import com.myeclipsedev.ui.internal.utils.ArchiveUtil;

public class ExportWarOp extends WorkspaceJob {

	private GWTCompileSettings compileSettings;
	private IProject project;
	private Path destination;
	private List<IPath> zipEntries = new ArrayList<IPath>();

	public ExportWarOp(IProject project, String destination,
			GWTCompileSettings compileSettings) {
		super("Export");
		this.project = project;
		this.compileSettings = compileSettings;
		this.destination = new Path(destination);
	}

	@Override
	public IStatus runInWorkspace(IProgressMonitor monitor)
			throws CoreException {

		try {
			// Remember the compilation settings for next time
			GWTProjectProperties
					.setGwtCompileSettings(project, compileSettings);
		} catch (BackingStoreException e) {
			// Failed to save project properties
			ExportActivator.logError(e);
		}

		final String taskName = GWTCompileRunner.computeTaskName(project);

		// Get a message console for GWT compiler output

		CustomMessageConsole messageConsole = MessageConsoleUtilities
				.getMessageConsole(taskName, null);

		TerminateProcessAction terminateAction = new TerminateProcessAction();
		messageConsole.setTerminateAction(terminateAction);

		messageConsole.activate();
		OutputStream consoleOutputStream = messageConsole.newMessageStream();

		try {
			IPath warLocation = null;

			if (WebAppUtilities.isWebApp(project)) {
				/*
				 * First, check the additional compiler arguments to see if the
				 * user specified the -war option manually. If not, use the
				 * project's managed WAR output directory (if set) or failing
				 * that, prompt for a file-system path.
				 */
				if (!compileSettings.getExtraArgs().contains("-war")) {
					warLocation = WebAppUtilities
							.getWarOutLocationOrPrompt(project);
					if (warLocation == null) {
						// User canceled the dialog
						return Status.OK_STATUS;
					}
				}
			}

			GWTCompileRunner.compileWithCancellationSupport(
					JavaCore.create(project), warLocation, compileSettings,
					consoleOutputStream, terminateAction, new SubProgressMonitor(monitor, 100),
					terminateAction);

			exportWar(monitor);

			return Status.OK_STATUS;

		} catch (IOException e) {
			ExportActivator.logError(e);
			throw new CoreException(new Status(IStatus.ERROR,
					ExportActivator.PLUGIN_ID, e.getLocalizedMessage(), e));
		} catch (InterruptedException e) {
			ExportActivator.logError(e);
			throw new CoreException(new Status(IStatus.ERROR,
					ExportActivator.PLUGIN_ID, e.getLocalizedMessage(), e));
		} catch (OperationCanceledException e) {
			// Ignore since the user canceled
			return Status.OK_STATUS;
		} catch (CoreException e) {
			ExportActivator.logError(e);
			return e.getStatus();
		} catch (ArchiveSaveFailureException e) {
			ExportActivator.logError(e);
			throw new CoreException(new Status(IStatus.ERROR,
					ExportActivator.PLUGIN_ID, e.getLocalizedMessage(), e));
		} finally {
			terminateAction.setEnabled(false);
			monitor.done();
			try {
				assert (consoleOutputStream != null);
				consoleOutputStream.close();
			} catch (IOException e) {
				// Ignore IOExceptions during stream close
			}
		}

	}

	private void exportWar(IProgressMonitor monitor) throws CoreException,
			ArchiveSaveFailureException {
		project.refreshLocal(IResource.DEPTH_INFINITE, new SubProgressMonitor(
				monitor, 100));
		IProgressMonitor subMonitor = new SubProgressMonitor(monitor, 1000);
		final int SAVE_TICKS = 198;
		final int CLEANUP_TICKS = 2;
		final int TOTAL_TICKS = SAVE_TICKS + CLEANUP_TICKS;
		try {
			File writeFile = destination.toFile();
			if (writeFile.exists()) {
				writeFile.delete();
			}
			java.io.File aFile = destination.toFile();
			ArchiveUtil.checkWriteable(aFile);
			boolean fileExisted = aFile.exists();
			ZipOutputStream zipOutputStream = null;
			try {
				java.io.File destinationFile = fileExisted ? ArchiveUtil
						.createTempFile(destination.toOSString(), aFile
								.getCanonicalFile().getParentFile()) : aFile;

				java.io.OutputStream out = createOutputStream(destinationFile);

				subMonitor.beginTask(
						NLS.bind("Exprt to war file {0}",
								destination.toOSString()), TOTAL_TICKS);

				zipOutputStream = new ZipOutputStream(out);
				saveArchive(zipOutputStream);
				subMonitor.worked(SAVE_TICKS);

				if (fileExisted) {
					ArchiveUtil.cleanupAfterTempSave(destination.toOSString(),
							aFile, destinationFile);
				}
				subMonitor.worked(CLEANUP_TICKS);
			} catch (java.io.IOException e) {
				throw new ArchiveSaveFailureException(e);
			} catch (ArchiveSaveFailureException failure) {
				try {
					if (zipOutputStream != null) {
						zipOutputStream.finish();
					}
				} catch (IOException weTried) {
					// Ignore
				}
				if (!fileExisted)
					aFile.delete();
				throw failure;
			} finally {
				if (zipOutputStream != null) {
					try {
						zipOutputStream.finish();
					} catch (IOException e) {
						ExportActivator.logError(e);
					}
				}
			}
		} finally {
			subMonitor.done();
		}
	}

	/**
	 * Fetches the resources for the component using the FlatVirtualComponent
	 * and saves them to an archive.
	 * 
	 * @param zipOutputStream
	 * 
	 * @throws ArchiveSaveFailureException
	 */
	protected void saveArchive(ZipOutputStream zipOutputStream)
			throws ArchiveSaveFailureException {
		Exception caughtException = null;
		boolean createManifest = true;
		try {
			IFolder folder = project.getFolder("war");
			if (folder.exists()) {
				IResource[] resources = folder.members(false);

				saveManifest(zipOutputStream, folder.getFolder("META-INF"),
						createManifest);

				saveFlatResources(zipOutputStream, folder, resources);
			}
		} catch (Exception e) {
			caughtException = e;
		} finally {
			if (caughtException != null) {
				throw new ArchiveSaveFailureException(caughtException);
			}
		}
	}

	private void saveFlatResources(ZipOutputStream zipOutputStream,
			IFolder folder, IResource[] resources)
			throws ArchiveSaveFailureException, CoreException {
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			IPath entryPath = resource.getProjectRelativePath().makeRelativeTo(
					folder.getProjectRelativePath());

			if (resource instanceof IFile) {
				if (shouldInclude(entryPath, true)) {
					addZipEntry(zipOutputStream, resource, entryPath);
					zipEntries.add(entryPath);
				}
			} else if (resource instanceof IFolder) {
				if (shouldInclude(entryPath, false)) {
					addZipEntry(zipOutputStream, resource, entryPath);
					zipEntries.add(entryPath);
					saveFlatResources(zipOutputStream, folder,
							((IFolder) resource).members());
				}
			}
		}
	}

	/**
	 * @param entryPath
	 * @param isFile
	 * @return true or false - should resource be added to the archive
	 */
	protected boolean shouldInclude(IPath entryPath, boolean isFile) {
		if (zipEntries.contains(entryPath)) {
			return false;
		}
		if (isFile) {
			if (entryPath.equals(new Path("META-INF/MANIFEST.MF"))) {
				return false;
			}
		} else if (entryPath.equals(new Path(".settings"))) {
			return false;
		}
		return true;
	}

	/**
	 * This method adds the existing MANIFEST.MF as the first entry in the
	 * archive. This is necessary to support clients who use
	 * JarInputStream.getManifest(). If no MANIFEST.MF is found, one is created
	 * if createManifest param is true
	 * 
	 * @param zipOutputStream
	 * 
	 * @param resources
	 * @param createManifest
	 * @throws ArchiveSaveFailureException
	 */
	private void saveManifest(ZipOutputStream zipOutputStream, IFolder metainf,
			boolean createManifest) throws ArchiveSaveFailureException {

		IFile manifest = null;
		if (metainf != null && metainf.exists()) {
			IResource[] children;
			try {
				children = metainf.members();
				for (int i = 0; i < children.length; i++) {
					if (children[i].getName().equals("MANIFEST.MF")) {
						manifest = (IFile) children[i];
						IPath entryPath = new Path(metainf.getName())
								.append(manifest.getName());
						addZipEntry(zipOutputStream, manifest, entryPath);
						break;
					}
				}
			} catch (CoreException e) {
				new ArchiveSaveFailureException(e);
			}

		}
		if (createManifest && manifest == null) {
			// manifest not found so create one for the archive
			createManifest(zipOutputStream);
		}
	}

	private void createManifest(ZipOutputStream zipOutputStream)
			throws ArchiveSaveFailureException {
		String manifestContents = "Manifest-Version: 1.0\r\n\r\n"; //$NON-NLS-1$
		try {
			ZipEntry entry = new ZipEntry("META-INF/MANIFEST.MF");
			zipOutputStream.putNextEntry(entry);
			ArchiveUtil.copy(
					new ByteArrayInputStream(manifestContents.getBytes()),
					zipOutputStream);
		} catch (IOException e) {
			throw new ArchiveSaveFailureException(e);
		}
	}

	/**
	 * Adds an entry and copies the resource into the archive
	 * 
	 * @param zipOutputStream
	 * 
	 * @param flatresource
	 * @param entryPath
	 * @throws ArchiveSaveFailureException
	 */
	protected void addZipEntry(ZipOutputStream zipOutputStream, IResource f,
			IPath entryPath) throws ArchiveSaveFailureException {
		try {
			IPath path = entryPath;
			boolean isFolder = false;
			long lastModified = 0;

			if (f instanceof IFolder) {
				isFolder = true;
				File folder = f.getFullPath().toFile();
				if (folder != null) {
					lastModified = folder.lastModified();
				}
				if (!path.hasTrailingSeparator())
					path = path.addTrailingSeparator();
			} else {
				lastModified = f.getFullPath().toFile().lastModified();
			}
			ZipEntry entry = new ZipEntry(path.toString());
			if (lastModified > 0)
				entry.setTime(lastModified);

			zipOutputStream.putNextEntry(entry);
			if (!isFolder) {
				ArchiveUtil.copy(new FileInputStream(f.getLocation().toFile()),
						zipOutputStream);
			}
			zipOutputStream.closeEntry();
		} catch (IOException e) {
			throw new ArchiveSaveFailureException(e);
		}
	}

	protected java.io.OutputStream createOutputStream(
			java.io.File destinationFile) throws IOException,
			FileNotFoundException {
		if (destinationFile.exists() && destinationFile.isDirectory()) {
			throw new IOException(NLS.bind(
					"Faild to create output for file {0}",
					destinationFile.getAbsolutePath()));
		}
		java.io.File parent = destinationFile.getParentFile();
		if (parent != null)
			parent.mkdirs();
		java.io.OutputStream out = new java.io.FileOutputStream(destinationFile);
		return out;
	}

}
