/**
 * Copyright (c) 2020-2024 Payara Foundation
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 */
package fish.payara.eclipse.tools.micro.ui.wizards;

import static fish.payara.eclipse.tools.micro.ui.wizards.MicroProjectWizard.ARCHETYPE_AUTOBIND_HTTP;
import static fish.payara.eclipse.tools.micro.ui.wizards.MicroProjectWizard.ARCHETYPE_CONTEXT_ROOT;
import static fish.payara.eclipse.tools.micro.ui.wizards.MicroProjectWizard.ARCHETYPE_MICRO_VERSION;
import static fish.payara.eclipse.tools.micro.ui.wizards.MicroProjectWizard.ARCHETYPE_VERSION_5X;
import static fish.payara.eclipse.tools.micro.ui.wizards.MicroProjectWizard.ARCHETYPE_VERSION_6X;
import static fish.payara.eclipse.tools.micro.ui.wizards.MicroProjectWizard.ARCHETYPE_GROUP_ID;
import static fish.payara.eclipse.tools.micro.ui.wizards.MicroProjectWizard.ARCHETYPE_ARTIFACT_ID;
import static fish.payara.eclipse.tools.micro.ui.wizards.MicroProjectWizard.STARTER_ARCHETYPE_GROUP_ID;
import static fish.payara.eclipse.tools.micro.ui.wizards.MicroProjectWizard.STARTER_ARCHETYPE_ARTIFACT_ID;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.maven.archetype.catalog.Archetype;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;
import org.eclipse.m2e.core.ui.internal.wizards.AbstractMavenWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import fish.payara.eclipse.tools.micro.MavenBuildTool;

public class MicroSettingsWizardPage extends AbstractMavenWizardPage {

	private Combo contextPathCombo;

	private Combo microVersionCombo;

	private Button autobindCheckbox;

	private Archetype archetype;

	public MicroSettingsWizardPage(ProjectImportConfiguration projectImportConfiguration) {
		super(MicroSettingsWizardPage.class.getSimpleName(), projectImportConfiguration);
		setTitle(Messages.microSettingsPageTitle);
		setDescription(Messages.microSettingsPageDescription);
		setPageComplete(false);
	}

	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NULL);
		composite.setLayout(new GridLayout(3, false));
		createUI(composite);
		validate();
		setControl(composite);
	}

	private void createUI(Composite parent) {
		Label contextPathlabel = new Label(parent, SWT.NONE);
		contextPathlabel.setText(Messages.contextPathComponentLabel);

		contextPathCombo = new Combo(parent, SWT.BORDER);
		contextPathCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		addFieldWithHistory(ARCHETYPE_CONTEXT_ROOT, contextPathCombo);
		contextPathCombo.setData("name", ARCHETYPE_CONTEXT_ROOT); //$NON-NLS-1$
		contextPathCombo.addModifyListener(e -> validate());

		Label microVersionLabel = new Label(parent, SWT.NONE);
		microVersionLabel.setText(Messages.microVersionComponentLabel);

		microVersionCombo = new Combo(parent, SWT.BORDER);
		microVersionCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));
		microVersionCombo.setData("name", ARCHETYPE_MICRO_VERSION); //$NON-NLS-1$
		microVersionCombo.addModifyListener(e -> validate());
		microVersionCombo.setItems(MicroProjectWizard.getVersions().toArray(new String[0]));

		Label autobindLabel = new Label(parent, SWT.NONE);
		autobindLabel.setText(Messages.autobindComponentLabel);

		autobindCheckbox = new Button(parent, SWT.CHECK);
		GridData gd_autobindCheckbox = new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1);
		gd_autobindCheckbox.widthHint = 150;
		autobindCheckbox.setLayoutData(gd_autobindCheckbox);
	}

	public void setVisible(boolean visible) {
		super.setVisible(visible);

		if (visible) {
			validate();
		}
	}

	void setArchetype(Archetype archetype) {
		this.archetype = archetype;
		contextPathCombo.setText((String) archetype.getProperties().get(ARCHETYPE_CONTEXT_ROOT));
		microVersionCombo.setText((String) archetype.getProperties().get(ARCHETYPE_MICRO_VERSION));
		autobindCheckbox.setSelection(Boolean.valueOf((String) archetype.getProperties().get(ARCHETYPE_AUTOBIND_HTTP)));
	}

	void validate() {
		if (isVisible()) {
			String error = validateInput();
			setErrorMessage(error);
			setPageComplete(error == null);
		}
	}

	private boolean isVisible() {
		return getControl() != null && getControl().isVisible();
	}

	private String validateInput() {
		String contextPathValue = contextPathCombo.getText().trim();
		if (contextPathValue.length() == 0) {
			return Messages.contextPathValidationMessage;
		}

		String versionValue = microVersionCombo.getText().trim();
		if (versionValue.length() == 0) {
			return Messages.microVersionValidationMessage;
		}

		return null;
	}

	public Archetype getArchetype() {
		String[] versionToken = microVersionCombo.getText().trim().split("\\.");
		if (versionToken.length > 1 && Integer.parseInt(versionToken[0]) < 6) {
			archetype.setGroupId(ARCHETYPE_GROUP_ID);
			archetype.setArtifactId(ARCHETYPE_ARTIFACT_ID);
			archetype.setVersion(ARCHETYPE_VERSION_5X);
			MavenBuildTool.setStartCommand("start");
		} else {
			archetype.setGroupId(STARTER_ARCHETYPE_GROUP_ID);
			archetype.setArtifactId(STARTER_ARCHETYPE_ARTIFACT_ID);
			archetype.setVersion(ARCHETYPE_VERSION_6X);
		}

		return archetype;
	}

	public Map<String, String> getProperties() {
		Map<String, String> properties = archetype.getProperties()
				.entrySet()
				.stream()
				.collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString()));
		String contextRoot = contextPathCombo.getText().trim();
		try {
			contextRoot = contextRoot.startsWith("/") ? '/' + URLEncoder.encode(contextRoot.substring(1), UTF_8.name())
					: URLEncoder.encode(contextRoot, UTF_8.name());
			properties.put(ARCHETYPE_CONTEXT_ROOT, contextRoot);
		} catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException("Invalid context root value " + contextRoot);
		}
		properties.put(ARCHETYPE_MICRO_VERSION, microVersionCombo.getText());
		properties.put(ARCHETYPE_AUTOBIND_HTTP, String.valueOf(autobindCheckbox.getSelection()));
		return properties;
	}
}
