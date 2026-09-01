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

package org.apache.hop.pipeline.transforms.kubernetes.list;

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

public class KubernetesListDialog extends BaseTransformDialog {

  private static final Class<?> PKG = KubernetesApiService.class;

  protected final KubernetesListMeta input;

  protected MetaSelectionLine<KubernetesConnection> wConnection;
  protected TextVar wKind;
  protected TextVar wApiVersion;
  protected TextVar wNamespace;
  protected TextVar wLabelSelector;
  protected TextVar wLimit;
  protected Button wIncludeStatus;

  public KubernetesListDialog(
      final Shell parent,
      final IVariables variables,
      final KubernetesListMeta transformMeta,
      final PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    input = transformMeta;
  }

  @Override
  public String open() {
    createShell(BaseMessages.getString(PKG, "KubernetesListDialog.Shell.Title"));
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
            BaseMessages.getString(PKG, "KubernetesListDialog.Connection.Label"),
            BaseMessages.getString(PKG, "KubernetesListDialog.Connection.Tooltip"));
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
    wSettings.setText(BaseMessages.getString(PKG, "KubernetesListDialog.Settings.Label"));
    PropsUi.setLook(wSettings);
    wSettings.setLayout(new FormLayout());
    final FormData fdSettings = new FormData();
    fdSettings.left = new FormAttachment(0, margin);
    fdSettings.right = new FormAttachment(100, -margin);
    fdSettings.top = new FormAttachment(wConnection, margin);
    wSettings.setLayoutData(fdSettings);

    wKind =
        addVarField(
            wSettings,
            "KubernetesListDialog.Kind.Label",
            "KubernetesListDialog.Kind.Tooltip",
            null);
    wApiVersion =
        addVarField(
            wSettings,
            "KubernetesListDialog.ApiVersion.Label",
            "KubernetesListDialog.ApiVersion.Tooltip",
            wKind);
    wNamespace =
        addVarField(
            wSettings,
            "KubernetesListDialog.Namespace.Label",
            "KubernetesListDialog.Namespace.Tooltip",
            wApiVersion);
    wLabelSelector =
        addVarField(
            wSettings,
            "KubernetesListDialog.LabelSelector.Label",
            "KubernetesListDialog.LabelSelector.Tooltip",
            wNamespace);
    wLimit =
        addVarField(
            wSettings,
            "KubernetesListDialog.Limit.Label",
            "KubernetesListDialog.Limit.Tooltip",
            wLabelSelector);

    wIncludeStatus = new Button(wSettings, SWT.CHECK | SWT.LEFT);
    wIncludeStatus.setText(BaseMessages.getString(PKG, "KubernetesListDialog.IncludeStatus.Label"));
    wIncludeStatus.setToolTipText(
        BaseMessages.getString(PKG, "KubernetesListDialog.IncludeStatus.Tooltip"));
    PropsUi.setLook(wIncludeStatus);
    final FormData fdIncludeStatus = new FormData();
    fdIncludeStatus.left = new FormAttachment(0, 0);
    fdIncludeStatus.top = new FormAttachment(wLimit, margin);
    wIncludeStatus.setLayoutData(fdIncludeStatus);
    wIncludeStatus.addSelectionListener(
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
    wKind.setText(Const.NVL(input.getKind(), ""));
    wApiVersion.setText(Const.NVL(input.getApiVersion(), ""));
    wNamespace.setText(Const.NVL(input.getNamespace(), ""));
    wLabelSelector.setText(Const.NVL(input.getLabelSelector(), ""));
    wLimit.setText(input.getLimit() > 0 ? Integer.toString(input.getLimit()) : "");
    wIncludeStatus.setSelection(input.isIncludeStatus());
  }

  protected void ok() {
    if (Utils.isEmpty(wConnection.getText())) {
      final MessageBox mb = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
      mb.setMessage(BaseMessages.getString(PKG, "KubernetesListDialog.Error.NoConnection"));
      mb.open();
      return;
    }
    getInfo(input);
    dispose();
  }

  protected void getInfo(final KubernetesListMeta in) {
    in.setConnectionName(wConnection.getText());
    in.setApiVersion(wApiVersion.getText());
    in.setKind(wKind.getText());
    in.setNamespace(wNamespace.getText());
    in.setLabelSelector(wLabelSelector.getText());
    final String limitText = wLimit.getText();
    in.setLimit(
        limitText == null || limitText.trim().isEmpty() ? 0 : Integer.parseInt(limitText.trim()));
    in.setIncludeStatus(wIncludeStatus.getSelection());
    in.setChanged();
  }

  protected void cancel() {
    transformName = null;
    input.setChanged(changed);
    dispose();
  }
}
