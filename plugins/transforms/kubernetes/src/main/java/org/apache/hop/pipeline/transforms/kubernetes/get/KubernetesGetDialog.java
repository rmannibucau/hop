/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hop.pipeline.transforms.kubernetes.get;

import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.kubernetes.metadata.KubernetesConnection;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transforms.kubernetes.KubernetesApiService;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

public class KubernetesGetDialog extends BaseTransformDialog {

  private static final Class<?> PKG = KubernetesApiService.class;

  protected final KubernetesGetMeta input;

  protected MetaSelectionLine<KubernetesConnection> wConnection;
  protected TextVar wApiVersion;
  protected TextVar wKind;
  protected TextVar wName;
  protected TextVar wNamespace;
  protected TextVar wLabelSelector;
  protected TextVar wOutputFieldName;
  protected Button wOutputJson;

  public KubernetesGetDialog(
      final Shell parent,
      final IVariables variables,
      final KubernetesGetMeta transformMeta,
      final PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    input = transformMeta;
  }

  @Override
  public String open() {
    createShell(BaseMessages.getString(PKG, "KubernetesGetDialog.Shell.Title"));
    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();

    final ModifyListener lsMod = e -> input.setChanged();
    changed = input.hasChanged();

    final Composite wComposite = new Composite(shell, SWT.NONE);
    PropsUi.setLook(wComposite);
    final FormData fdComposite = new FormData();
    fdComposite.left = new FormAttachment(0, 0);
    fdComposite.right = new FormAttachment(100, 0);
    fdComposite.top = new FormAttachment(wSpacer, 0);
    fdComposite.bottom = new FormAttachment(wOk, -margin);
    wComposite.setLayoutData(fdComposite);
    final FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    wComposite.setLayout(formLayout);

    wConnection =
        new MetaSelectionLine<>(
            variables,
            metadataProvider,
            KubernetesConnection.class,
            wComposite,
            SWT.NONE,
            BaseMessages.getString(PKG, "KubernetesGetDialog.Connection.Label"),
            BaseMessages.getString(PKG, "KubernetesGetDialog.Connection.Tooltip"));
    PropsUi.setLook(wConnection);
    final FormData fdConnection = new FormData();
    fdConnection.left = new FormAttachment(0, 0);
    fdConnection.right = new FormAttachment(100, 0);
    fdConnection.top = new FormAttachment(0, margin);
    wConnection.setLayoutData(fdConnection);
    try {
      wConnection.fillItems();
    } catch (final HopException e) {
      final MessageBox mb = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
      mb.setMessage(BaseMessages.getString(PKG, "Error.GettingConnections", e.getMessage()));
      mb.open();
    }
    wConnection.addModifyListener(lsMod);

    final Group wSettings = new Group(wComposite, SWT.SHADOW_ETCHED_IN);
    wSettings.setText(BaseMessages.getString(PKG, "KubernetesGetDialog.Settings.Label"));
    PropsUi.setLook(wSettings);
    wSettings.setLayout(new FormLayout());
    final FormData fdSettings = new FormData();
    fdSettings.left = new FormAttachment(0, margin);
    fdSettings.right = new FormAttachment(100, -margin);
    fdSettings.top = new FormAttachment(wConnection, margin);
    wSettings.setLayoutData(fdSettings);

    wKind =
        addVarField(
            wSettings, "KubernetesGetDialog.Kind.Label", "KubernetesGetDialog.Kind.Tooltip", null);
    wApiVersion =
        addVarField(
            wSettings,
            "KubernetesGetDialog.ApiVersion.Label",
            "KubernetesGetDialog.ApiVersion.Tooltip",
            wKind);
    wName =
        addVarField(
            wSettings,
            "KubernetesGetDialog.Name.Label",
            "KubernetesGetDialog.Name.Tooltip",
            wApiVersion);
    wNamespace =
        addVarField(
            wSettings,
            "KubernetesGetDialog.Namespace.Label",
            "KubernetesGetDialog.Namespace.Tooltip",
            wName);
    wLabelSelector =
        addVarField(
            wSettings,
            "KubernetesGetDialog.LabelSelector.Label",
            "KubernetesGetDialog.LabelSelector.Tooltip",
            wNamespace);
    wOutputFieldName =
        addVarField(
            wSettings,
            "KubernetesGetDialog.OutputFieldName.Label",
            "KubernetesGetDialog.OutputFieldName.Tooltip",
            wLabelSelector);

    wOutputJson = new Button(wSettings, SWT.CHECK | SWT.LEFT);
    wOutputJson.setText(BaseMessages.getString(PKG, "KubernetesGetDialog.OutputJson.Label"));
    wOutputJson.setToolTipText(
        BaseMessages.getString(PKG, "KubernetesGetDialog.OutputJson.Tooltip"));
    PropsUi.setLook(wOutputJson);
    final FormData fdOutputJson = new FormData();
    fdOutputJson.left = new FormAttachment(0, 0);
    fdOutputJson.top = new FormAttachment(wOutputFieldName, margin);
    wOutputJson.setLayoutData(fdOutputJson);
    wOutputJson.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(final SelectionEvent e) {
            input.setChanged();
          }
        });

    getData();
    focusTransformName();
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());
    return transformName;
  }

  protected TextVar addVarField(
      final Composite parent,
      final String labelKey,
      final String tooltipKey,
      final TextVar previous) {
    final Label wlField = new Label(parent, SWT.RIGHT);
    wlField.setText(BaseMessages.getString(PKG, labelKey));
    wlField.setToolTipText(BaseMessages.getString(PKG, tooltipKey));
    PropsUi.setLook(wlField);
    final TextVar wField = new TextVar(variables, parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    wField.setToolTipText(BaseMessages.getString(PKG, tooltipKey));
    PropsUi.setLook(wField);
    final FormData fdLabel = new FormData();
    fdLabel.left = new FormAttachment(0, 0);
    fdLabel.right = new FormAttachment(35, 0);
    if (previous == null) {
      fdLabel.top = new FormAttachment(0, 0);
    } else {
      fdLabel.top = new FormAttachment(previous, margin);
    }
    wlField.setLayoutData(fdLabel);
    final FormData fdField = new FormData();
    fdField.left = new FormAttachment(wlField, margin);
    fdField.right = new FormAttachment(100, 0);
    fdField.top = new FormAttachment(wlField, 0, SWT.CENTER);
    wField.setLayoutData(fdField);
    wField.addModifyListener(e -> input.setChanged());
    return wField;
  }

  protected void getData() {
    wConnection.setText(Const.NVL(input.getConnectionName(), ""));
    wApiVersion.setText(Const.NVL(input.getApiVersion(), ""));
    wKind.setText(Const.NVL(input.getKind(), ""));
    wName.setText(Const.NVL(input.getName(), ""));
    wNamespace.setText(Const.NVL(input.getNamespace(), ""));
    wLabelSelector.setText(Const.NVL(input.getLabelSelector(), ""));
    wOutputFieldName.setText(Const.NVL(input.getOutputFieldName(), "resource_manifest"));
    wOutputJson.setSelection(input.isOutputJson());
  }

  protected void ok() {
    if (Utils.isEmpty(wConnection.getText())) {
      final MessageBox mb = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
      mb.setMessage(BaseMessages.getString(PKG, "KubernetesGetDialog.Error.NoConnection"));
      mb.open();
      return;
    }
    getInfo(input);
    dispose();
  }

  protected void getInfo(final KubernetesGetMeta in) {
    in.setConnectionName(wConnection.getText());
    in.setApiVersion(wApiVersion.getText());
    in.setKind(wKind.getText());
    in.setName(wName.getText());
    in.setNamespace(wNamespace.getText());
    in.setLabelSelector(wLabelSelector.getText());
    final String outputField = wOutputFieldName.getText();
    in.setOutputFieldName(Utils.isEmpty(outputField) ? "resource_manifest" : outputField);
    in.setOutputJson(wOutputJson.getSelection());
    in.setChanged();
  }

  protected void cancel() {
    transformName = null;
    input.setChanged(changed);
    dispose();
  }
}
