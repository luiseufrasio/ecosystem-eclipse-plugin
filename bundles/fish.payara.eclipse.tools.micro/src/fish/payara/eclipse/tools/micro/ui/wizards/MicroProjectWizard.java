/**
 * Copyright (c) 2020-2024 Payara Foundation
 * Copyright (c) 2008-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 */
package fish.payara.eclipse.tools.micro.ui.wizards;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.toList;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.archetype.catalog.Archetype;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.eclipse.m2e.core.project.IMavenProjectImportResult;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.ui.internal.wizards.MavenProjectWorkspaceAssigner;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;
import org.eclipse.m2e.core.ui.internal.actions.SelectionUtil;
import org.eclipse.m2e.core.ui.internal.archetype.MavenArchetype;
import org.eclipse.m2e.core.ui.internal.archetype.ArchetypeGenerator;
import org.eclipse.m2e.core.ui.internal.archetype.ArchetypePlugin;
import org.eclipse.m2e.core.ui.internal.wizards.AbstractCreateMavenProjectJob;
import org.eclipse.m2e.core.ui.internal.wizards.MappingDiscoveryJob;
import org.eclipse.m2e.core.ui.internal.wizards.MavenProjectWizardLocationPage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkingSet;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class MicroProjectWizard extends Wizard implements INewWizard {

	protected List<IWorkingSet> workingSets = new ArrayList<IWorkingSet>();

	public MicroProjectWizard() {
		setNeedsProgressMonitor(true);
		setWindowTitle(Messages.microProjectTitle);
	}

	private MavenProjectWizardLocationPage projectLocationPage;
	private MicroProjectWizardPage projectSettingsPage;
	private MicroSettingsWizardPage microSettingsPage;
	private ProjectImportConfiguration importConfiguration;
	private IStructuredSelection selection;

	private static final String GROUP_ID = "\\[groupId\\]"; //$NON-NLS-1$
	private static final String ARTIFACT_ID = "\\[artifactId\\]"; //$NON-NLS-1$
	private static final String VERSION = "\\[version\\]"; //$NON-NLS-1$
	private static final String NAME = "\\[name\\]"; //$NON-NLS-1$
	private static final String PROJECT_NAME_REGEX = "\\$\\{[^\\}]++\\}"; //$NON-NLS-1$

	public static final String ARCHETYPE_GROUP_ID = "fish.payara.maven.archetypes"; //$NON-NLS-1$
	public static final String ARCHETYPE_ARTIFACT_ID = "payara-micro-maven-archetype"; //$NON-NLS-1$
	public static final String ARCHETYPE_VERSION_5X = "1.5.0"; //$NON-NLS-1$
	public static final String STARTER_ARCHETYPE_GROUP_ID = "fish.payara.starter";
	public static final String STARTER_ARCHETYPE_ARTIFACT_ID = "payara-starter-archetype";
	public static final String ARCHETYPE_VERSION_6X = "1.0-beta9"; //$NON-NLS-1$
	private static final String ARCHETYPE_JDK_VERSION = "jdkVersion"; //$NON-NLS-1$
	private static final String ARCHETYPE_JDK_VERSION_DEFAULT_VALUE = "1.8"; //$NON-NLS-1$
	public static final String ARCHETYPE_MICRO_VERSION = "payaraMicroVersion"; //$NON-NLS-1$
	public static final String ARCHETYPE_AUTOBIND_HTTP = "autoBindHttp"; //$NON-NLS-1$
	private static final String ARCHETYPE_CONCURRENT_API = "addConcurrentApi"; //$NON-NLS-1$
	private static final String ARCHETYPE_RESOURCE_API = "addResourceApi"; //$NON-NLS-1$
	private static final String ARCHETYPE_JBATCH_API = "addJBatchApi"; //$NON-NLS-1$
	private static final String ARCHETYPE_MICROPROFILE_API = "addMicroprofileApi"; //$NON-NLS-1$
	private static final String ARCHETYPE_JCACHE = "addJcache"; //$NON-NLS-1$
	private static final String ARCHETYPE_PAYARA_API = "addPayaraApi"; //$NON-NLS-1$
	public static final String ARCHETYPE_CONTEXT_ROOT = "contextRoot"; //$NON-NLS-1$
	private static final String ARCHETYPE_CONTEXT_ROOT_DEFAULT_VALUE = "/"; //$NON-NLS-1$
	private static final String ARCHETYPE_DEPLOY_WAR = "deployWar"; //$NON-NLS-1$
	private static final String EMPTY = ""; //$NON-NLS-1$


	public static final String DEFAULT_REPOSITORY_URL = "https://repo1.maven.org/maven2/"; // NOI18N

	private static final String METADATA_URL = "fish/payara/extras/payara-micro/maven-metadata.xml"; // NOI18N

	private static final List<String> versions = new ArrayList<>();
	public static List<String> getVersions() {
		if (versions.isEmpty()) {
			try {
				// Construct the full URL
				String urlString = DEFAULT_REPOSITORY_URL + METADATA_URL;
				URL url = new URL(urlString);

				// Open connection
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				connection.setRequestMethod("GET");

				// Read the response
				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				StringBuilder xmlResponse = new StringBuilder();
				String line;
				while ((line = in.readLine()) != null) {
					xmlResponse.append(line);
				}
				in.close();

				// Parse the XML response
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document doc = builder.parse(new InputSource(new StringReader(xmlResponse.toString())));

				String latest = doc.getElementsByTagName("latest").item(0).getTextContent();
				// Extract versions
				NodeList versionNodes = doc.getElementsByTagName("version");
				for (int i = versionNodes.getLength() - 1; i >= 0; i--) {
					String version = versionNodes.item(i).getTextContent();
					if ((version.contains("Alpha") || version.contains("Beta") || version.contains("SNAPSHOT")) // NOI18N
							&& !version.equals(latest)) {
						continue;
					};
					versions.add(version);
				}

			} catch (Exception e) {
				e.printStackTrace();
			}

		}
		return versions;
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		this.selection = selection;
		this.importConfiguration = new ProjectImportConfiguration();
		IWorkingSet workingSet = SelectionUtil.getSelectedWorkingSet(selection);
		if (workingSet != null) {
			this.workingSets.add(workingSet);
		}
	}

	@Override
	public void addPages() {
		projectLocationPage = new MavenProjectWizardLocationPage(importConfiguration,
				Messages.microProjectLocationPageTitle, Messages.microProjectLocationPageDescription, workingSets);
		projectLocationPage.setLocationPath(SelectionUtil.getSelectedLocation(selection));
		projectSettingsPage = new MicroProjectWizardPage(importConfiguration);
		microSettingsPage = new MicroSettingsWizardPage(importConfiguration);
		addPage(projectLocationPage);
		addPage(projectSettingsPage);
		addPage(microSettingsPage);
	}

	public void createPageControls(Composite pageContainer) {
		super.createPageControls(pageContainer);
		microSettingsPage.setArchetype(getArchetype());
	}

	private Archetype getArchetype() {
		Archetype archetype = new Archetype();
		archetype.setGroupId(STARTER_ARCHETYPE_GROUP_ID);
		archetype.setArtifactId(STARTER_ARCHETYPE_ARTIFACT_ID);
		archetype.setVersion(ARCHETYPE_VERSION_6X);
		Properties properties = new Properties();
		properties.put(ARCHETYPE_JDK_VERSION, ARCHETYPE_JDK_VERSION_DEFAULT_VALUE);
		properties.put(ARCHETYPE_MICRO_VERSION, getVersions().get(0));
		properties.put(ARCHETYPE_AUTOBIND_HTTP, TRUE.toString());
		properties.put(ARCHETYPE_CONCURRENT_API, FALSE.toString());
		properties.put(ARCHETYPE_RESOURCE_API, FALSE.toString());
		properties.put(ARCHETYPE_JBATCH_API, FALSE.toString());
		properties.put(ARCHETYPE_MICROPROFILE_API, TRUE.toString());
		properties.put(ARCHETYPE_JCACHE, FALSE.toString());
		properties.put(ARCHETYPE_PAYARA_API, TRUE.toString());
		properties.put(ARCHETYPE_CONTEXT_ROOT, ARCHETYPE_CONTEXT_ROOT_DEFAULT_VALUE);
		properties.put(ARCHETYPE_DEPLOY_WAR, TRUE.toString());
		archetype.setProperties(properties);
		return archetype;
	}

	@Override
	public boolean performFinish() {
		Archetype archetype = microSettingsPage.getArchetype();
		final String groupId = projectSettingsPage.getGroupId();
		final String artifactId = projectSettingsPage.getArtifactId();
		final String version = projectSettingsPage.getVersion();
		final String javaPackage = projectSettingsPage.getJavaPackage();
		final Map<String, String> properties = microSettingsPage.getProperties();
                
		final IPath location = projectLocationPage.isInWorkspace() ? null : projectLocationPage.getLocationPath();
		final String projectName = getProjectName(importConfiguration, groupId, artifactId, version);
		final IWorkspace workspace = ResourcesPlugin.getWorkspace();
		final IWorkspaceRoot root = workspace.getRoot();
		final IProject project = root.getProject(artifactId);

		boolean pomExists = (projectLocationPage.isInWorkspace() ? root.getLocation().append(project.getName())
				: location).append(IMavenConstants.POM_FILE_NAME).toFile().exists();
		if (pomExists) {
			MessageDialog.openError(getShell(), NLS.bind(Messages.projectArchetypeJobFailed, projectName),
					Messages.projectPomAlreadyExists);
			return false;
		}

		AbstractCreateMavenProjectJob job = new AbstractCreateMavenProjectJob(
				NLS.bind(Messages.projectArchetypeJobCreating, archetype.getArtifactId())) {
			@Override
			protected List<IProject> doCreateMavenProjects(IProgressMonitor monitor) throws CoreException {

				ArchetypePlugin archetypeManager = org.eclipse.m2e.core.ui.internal.M2EUIPluginActivator
						.getDefault().getArchetypePlugin();
				ArchetypeGenerator generator = archetypeManager.getGenerator();
//createArchetypeProjects(IPath location, IArchetype archetype, String groupId, String artifactId, String version, String javaPackage, Map<String, String> properties, IProgressMonitor monitor) throws CoreException {
				Collection<MavenProjectInfo> projects = generator.createArchetypeProjects(
						location, new MavenArchetype(archetype), groupId, artifactId, version, javaPackage, properties,
						monitor);
				return MavenPlugin.getProjectConfigurationManager()
						.importProjects(projects, importConfiguration,
								new MavenProjectWorkspaceAssigner(workingSets),
								monitor)
						.stream().filter(r -> r.getProject() != null && r.getProject().exists())
						.map(IMavenProjectImportResult::getProject).collect(toList());
			}
		};

		job.addJobChangeListener(new JobChangeAdapter() {
			public void done(IJobChangeEvent event) {
				final IStatus result = event.getResult();
				if (!result.isOK()) {
					Display.getDefault().asyncExec(() -> MessageDialog.openError(getShell(),
							NLS.bind(Messages.projectArchetypeJobFailed, projectName), result.getMessage()));
				}

				MappingDiscoveryJob discoveryJob = new MappingDiscoveryJob(job.getCreatedProjects());
				discoveryJob.schedule();

			}
		});

		job.setRule(MavenPlugin.getProjectConfigurationManager().getRule());
		job.schedule();

		return true;
	}

	public String getProjectName(ProjectImportConfiguration importConfiguration, String groupId, String artifactId,
			String version) {
		if (importConfiguration.getProjectNameTemplate().length() == 0) {
			return artifactId.replaceAll(PROJECT_NAME_REGEX, EMPTY);
		}
		return importConfiguration.getProjectNameTemplate()
				.replaceAll(GROUP_ID, groupId.replaceAll(PROJECT_NAME_REGEX, EMPTY))
				.replaceAll(ARTIFACT_ID, artifactId.replaceAll(PROJECT_NAME_REGEX, EMPTY))
				.replaceAll(NAME, artifactId.replaceAll(PROJECT_NAME_REGEX, EMPTY))
				.replaceAll(VERSION, version.replaceAll(PROJECT_NAME_REGEX, EMPTY));
	}

}
