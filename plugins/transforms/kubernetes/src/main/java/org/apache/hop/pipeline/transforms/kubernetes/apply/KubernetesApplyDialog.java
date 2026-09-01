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

package org.apache.hop.pipeline.transforms.kubernetes.apply;

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
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

public class KubernetesApplyDialog extends BaseTransformDialog {

  private static final Class<?> PKG = KubernetesApiService.class;

  protected final KubernetesApplyMeta input;

  protected MetaSelectionLine<KubernetesConnection> wConnection;
  protected TextVar wManifestFieldName;
  protected TextVar wNamespace;
  protected TextVar wOutputFieldName;
  protected TextVar wWaitTimeoutSeconds;
  protected Button wWaitReady;
  protected TextVar wAwaitField;
  protected TextVar wAwaitValue;

  public KubernetesApplyDialog(
      final Shell parent,
      final IVariables variables,
      final KubernetesApplyMeta transformMeta,
      final PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    input = transformMeta;
  }

  @Override
  public String open() {
    createShell(BaseMessages.getString(PKG, "KubernetesApplyDialog.Shell.Title"));
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
            BaseMessages.getString(PKG, "KubernetesApplyDialog.Connection.Label"),
            BaseMessages.getString(PKG, "KubernetesApplyDialog.Connection.Tooltip"));
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
    wSettings.setText(BaseMessages.getString(PKG, "KubernetesApplyDialog.Settings.Label"));
    PropsUi.setLook(wSettings);
    wSettings.setLayout(new FormLayout());
    final FormData fdSettings = new FormData();
    fdSettings.left = new FormAttachment(0, margin);
    fdSettings.right = new FormAttachment(100, -margin);
    fdSettings.top = new FormAttachment(wConnection, margin);
    wSettings.setLayoutData(fdSettings);

    wManifestFieldName =
        addVarField(
            wSettings,
            "KubernetesApplyDialog.ManifestFieldName.Label",
            "KubernetesApplyDialog.ManifestFieldName.Tooltip",
            null);
    wNamespace =
        addVarField(
            wSettings,
            "KubernetesApplyDialog.Namespace.Label",
            "KubernetesApplyDialog.Namespace.Tooltip",
            wManifestFieldName);
    wOutputFieldName =
        addVarField(
            wSettings,
            "KubernetesApplyDialog.OutputFieldName.Label",
            "KubernetesApplyDialog.OutputFieldName.Tooltip",
            wNamespace);
    wWaitTimeoutSeconds =
        addVarField(
            wSettings,
            "KubernetesApplyDialog.WaitTimeoutSeconds.Label",
            "KubernetesApplyDialog.WaitTimeoutSeconds.Tooltip",
            wOutputFieldName);

    wWaitReady = new Button(wSettings, SWT.CHECK | SWT.LEFT);
    wWaitReady.setText(BaseMessages.getString(PKG, "KubernetesApplyDialog.WaitReady.Label"));
    wWaitReady.setToolTipText(
        BaseMessages.getString(PKG, "KubernetesApplyDialog.WaitReady.Tooltip"));
    PropsUi.setLook(wWaitReady);
    final FormData fdWaitReady = new FormData();
    fdWaitReady.left = new FormAttachment(0, 0);
    fdWaitReady.top = new FormAttachment(wWaitTimeoutSeconds, margin);
    wWaitReady.setLayoutData(fdWaitReady);
    wWaitReady.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(final SelectionEvent e) {
            input.setChanged();
          }
        });

    wAwaitField =
        addVarField(
            wSettings,
            "KubernetesApplyDialog.AwaitField.Label",
            "KubernetesApplyDialog.AwaitField.Tooltip",
            wWaitTimeoutSeconds,
            wWaitReady);
    wAwaitValue =
        addVarField(
            wSettings,
            "KubernetesApplyDialog.AwaitValue.Label",
            "KubernetesApplyDialog.AwaitValue.Tooltip",
            wAwaitField,
            null);

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

  protected TextVar addVarField(
      final Composite parent,
      final String labelKey,
      final String tooltipKey,
      final TextVar previous,
      final Control anchor) {
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
    final Control top = anchor != null ? anchor : previous;
    if (top == null) {
      fdLabel.top = new FormAttachment(0, margin);
    } else {
      fdLabel.top = new FormAttachment(top, margin);
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
    wManifestFieldName.setText(Const.NVL(input.getManifestFieldName(), "manifest"));
    wNamespace.setText(Const.NVL(input.getNamespace(), ""));
    wOutputFieldName.setText(Const.NVL(input.getOutputFieldName(), "result"));
    wWaitTimeoutSeconds.setText(
        input.getWaitTimeoutSeconds() > 0
            ? Integer.toString(input.getWaitTimeoutSeconds())
            : "120");
    wWaitReady.setSelection(input.isWaitReady());
    wAwaitField.setText(Const.NVL(input.getAwaitField(), ""));
    wAwaitValue.setText(Const.NVL(input.getAwaitValue(), ""));
  }

  protected void ok() {
    if (Utils.isEmpty(wConnection.getText())) {
      final MessageBox mb = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
      mb.setMessage(BaseMessages.getString(PKG, "KubernetesApplyDialog.Error.NoConnection"));
      mb.open();
      return;
    }
    getInfo(input);
    dispose();
  }

  protected void getInfo(final KubernetesApplyMeta in) {
    in.setConnectionName(wConnection.getText());
    in.setManifestFieldName(wManifestFieldName.getText());
    in.setNamespace(wNamespace.getText());
    in.setOutputFieldName(wOutputFieldName.getText());
    final String timeout = wWaitTimeoutSeconds.getText();
    in.setWaitTimeoutSeconds(
        timeout == null || timeout.trim().isEmpty() ? 120 : Integer.parseInt(timeout.trim()));
    in.setWaitReady(wWaitReady.getSelection());
    in.setAwaitField(wAwaitField.getText());
    in.setAwaitValue(wAwaitValue.getText());
    in.setChanged();
  }

  protected void cancel() {
    transformName = null;
    input.setChanged(changed);
    dispose();
  }
}
