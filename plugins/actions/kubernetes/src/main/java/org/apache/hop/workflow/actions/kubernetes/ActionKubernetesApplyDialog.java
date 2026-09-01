/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.hop.workflow.actions.kubernetes;

import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.kubernetes.metadata.KubernetesConnection;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.workflow.action.ActionDialog;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.IAction;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/** Dialog for the Kubernetes Apply action. */
public class ActionKubernetesApplyDialog extends ActionDialog {

  private static final Class<?> PKG = ActionKubernetesApplyDialog.class;

  private ActionKubernetesApply action;
  private MetaSelectionLine<KubernetesConnection> wConnection;
  private TextVar wManifestFile;
  private TextVar wFolder;
  private Text wInlineManifest;
  private TextVar wNamespace;
  private Button wWaitReady;
  private Text wRolloutTimeoutSeconds;
  private TextVar wAwaitField;
  private TextVar wAwaitValue;
  private boolean changed;

  public ActionKubernetesApplyDialog(
      Shell parent, ActionKubernetesApply action, WorkflowMeta workflowMeta, IVariables variables) {
    super(parent, workflowMeta, variables);
    this.action = action;
    if (action.getName() == null) {
      action.setName(BaseMessages.getString(PKG, "ActionKubernetesApply.Name"));
    }
  }

  @Override
  public IAction open() {
    createShell(BaseMessages.getString(PKG, "ActionKubernetesApply.Dialog.Title"), action);
    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();
    createGeneralTab();
    getData();
    return action;
  }

  private void createGeneralTab() {
    int middle = 10;
    int margin = PropsUi.getMargin();
    Composite wGeneralComp = new Composite(shell, SWT.NONE);
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = margin;
    formLayout.marginHeight = margin;
    wGeneralComp.setLayout(formLayout);
    PropsUi.setLook(wGeneralComp);

    int labelWidth = 180;

    wConnection =
        new MetaSelectionLine<>(
            variables,
            metadataProvider,
            KubernetesConnection.class,
            wGeneralComp,
            SWT.NONE,
            BaseMessages.getString(PKG, "ActionKubernetesApply.Dialog.Connection.Label"),
            null);
    PropsUi.setLook(wConnection);
    FormData fdConnection = new FormData();
    fdConnection.left = new FormAttachment(0, 0);
    fdConnection.right = new FormAttachment(100, -margin);
    fdConnection.top = new FormAttachment(0, 0);
    wConnection.setLayoutData(fdConnection);
    try {
      wConnection.fillItems();
    } catch (Exception e) {
      // ignored, connection list failure should not block the dialog
    }
    Control last = wConnection;

    last = addLabel(wGeneralComp, "ActionKubernetesApply.Dialog.ManifestFile.Label", last, margin);
    wManifestFile = addTextVar(wGeneralComp, last, margin, labelWidth);
    last = wManifestFile;

    last = addLabel(wGeneralComp, "ActionKubernetesApply.Dialog.Folder.Label", last, margin);
    wFolder = addTextVar(wGeneralComp, last, margin, labelWidth);
    last = wFolder;

    last =
        addLabel(wGeneralComp, "ActionKubernetesApply.Dialog.InlineManifest.Label", last, margin);
    wInlineManifest = addText(wGeneralComp, last, margin, labelWidth, 4);
    last = wInlineManifest;

    last = addLabel(wGeneralComp, "ActionKubernetesApply.Dialog.Namespace.Label", last, margin);
    wNamespace = addTextVar(wGeneralComp, last, margin, labelWidth);
    last = wNamespace;

    wWaitReady =
        addCheck(wGeneralComp, "ActionKubernetesApply.Dialog.WaitReady.Label", last, margin);
    last = wWaitReady;

    last =
        addLabel(
            wGeneralComp, "ActionKubernetesApply.Dialog.RolloutTimeoutSeconds.Label", last, margin);
    wRolloutTimeoutSeconds = addInt(wGeneralComp, last, margin);
    last = wRolloutTimeoutSeconds;

    last = addLabel(wGeneralComp, "ActionKubernetesApply.Dialog.AwaitField.Label", last, margin);
    wAwaitField = addTextVar(wGeneralComp, last, margin, labelWidth);
    last = wAwaitField;

    last = addLabel(wGeneralComp, "ActionKubernetesApply.Dialog.AwaitValue.Label", last, margin);
    wAwaitValue = addTextVar(wGeneralComp, last, margin, labelWidth);
    last = wAwaitValue;
  }

  public void getData() {
    wConnection.setText(Const.NVL(action.getConnectionName(), ""));
    wManifestFile.setText(Const.NVL(action.getManifestFile(), ""));
    wFolder.setText(Const.NVL(action.getFolder(), ""));
    wInlineManifest.setText(Const.NVL(action.getInlineManifest(), ""));
    wNamespace.setText(Const.NVL(action.getNamespace(), ""));
    wWaitReady.setSelection(action.isWaitReady());
    wRolloutTimeoutSeconds.setText(
        action.getRolloutTimeoutSeconds() > 0
            ? Integer.toString(action.getRolloutTimeoutSeconds())
            : "");
    wAwaitField.setText(Const.NVL(action.getAwaitField(), ""));
    wAwaitValue.setText(Const.NVL(action.getAwaitValue(), ""));
  }

  private void ok() {
    if (action.getConnectionName() == null && Utils.isEmpty(wConnection.getText())) {
      return;
    }
    action.setConnectionName(wConnection.getText());
    action.setManifestFile(wManifestFile.getText());
    action.setFolder(wFolder.getText());
    action.setInlineManifest(wInlineManifest.getText());
    action.setNamespace(wNamespace.getText());
    action.setWaitReady(wWaitReady.getSelection());
    String timeout = wRolloutTimeoutSeconds.getText();
    action.setRolloutTimeoutSeconds(Utils.isEmpty(timeout) ? 0 : Integer.parseInt(timeout.trim()));
    action.setAwaitField(wAwaitField.getText());
    action.setAwaitValue(wAwaitValue.getText());
    dispose();
  }

  private void cancel() {
    dispose();
  }

  private String lastTooltipKey;

  private Control addLabel(Composite parent, String key, Control top, int margin) {
    lastTooltipKey = tooltipKey(key);
    Label label = new Label(parent, SWT.LEFT);
    label.setText(BaseMessages.getString(PKG, key));
    label.setToolTipText(BaseMessages.getString(PKG, lastTooltipKey));
    PropsUi.setLook(label);
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.top = new FormAttachment(top, margin);
    label.setLayoutData(fd);
    return label;
  }

  private TextVar addTextVar(Composite parent, Control top, int margin, int labelWidth) {
    TextVar tv = new TextVar(variables, parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    if (lastTooltipKey != null) {
      tv.setToolTipText(BaseMessages.getString(PKG, lastTooltipKey));
    }
    PropsUi.setLook(tv);
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.right = new FormAttachment(100, -margin);
    fd.top = new FormAttachment(top, margin);
    tv.setLayoutData(fd);
    tv.addModifyListener(lsMod);
    return tv;
  }

  private Text addText(Composite parent, Control top, int margin, int labelWidth, int lines) {
    Text text = new Text(parent, SWT.MULTI | SWT.LEFT | SWT.BORDER | SWT.V_SCROLL);
    if (lastTooltipKey != null) {
      text.setToolTipText(BaseMessages.getString(PKG, lastTooltipKey));
    }
    PropsUi.setLook(text);
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.right = new FormAttachment(100, -margin);
    fd.top = new FormAttachment(top, margin);
    fd.height = lines * 14;
    text.setLayoutData(fd);
    text.addModifyListener(lsMod);
    return text;
  }

  private Text addInt(Composite parent, Control top, int margin) {
    Text text = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    if (lastTooltipKey != null) {
      text.setToolTipText(BaseMessages.getString(PKG, lastTooltipKey));
    }
    PropsUi.setLook(text);
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.top = new FormAttachment(top, margin);
    text.setLayoutData(fd);
    text.addModifyListener(lsMod);
    return text;
  }

  private Button addCheck(Composite parent, String key, Control top, int margin) {
    Button check = new Button(parent, SWT.CHECK);
    check.setText(BaseMessages.getString(PKG, key));
    check.setToolTipText(BaseMessages.getString(PKG, tooltipKey(key)));
    PropsUi.setLook(check);
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.top = new FormAttachment(top, margin);
    check.setLayoutData(fd);
    return check;
  }

  private String tooltipKey(final String labelKey) {
    return labelKey.endsWith(".Label")
        ? labelKey.substring(0, labelKey.length() - ".Label".length()) + ".Tooltip"
        : labelKey + ".Tooltip";
  }

  private ModifyListener lsMod = e -> changed = true;
}
