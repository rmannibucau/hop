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
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/** Dialog for the Kubernetes Exec action. */
public class ActionKubernetesExecDialog extends ActionDialog {

  private static final Class<?> PKG = ActionKubernetesExecDialog.class;

  private ActionKubernetesExec action;
  private MetaSelectionLine<KubernetesConnection> wConnection;
  private TextVar wPod;
  private TextVar wContainer;
  private TextVar wNamespace;
  private TextVar wCommand;
  private Text wWaitTimeoutSeconds;
  private boolean changed;

  public ActionKubernetesExecDialog(
      Shell parent, ActionKubernetesExec action, WorkflowMeta workflowMeta, IVariables variables) {
    super(parent, workflowMeta, variables);
    this.action = action;
    if (action.getName() == null) {
      action.setName(BaseMessages.getString(PKG, "ActionKubernetesExec.Name"));
    }
  }

  @Override
  public IAction open() {
    createShell(BaseMessages.getString(PKG, "ActionKubernetesExec.Dialog.Title"), action);
    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();
    int margin = PropsUi.getMargin();
    Composite wGeneralComp = new Composite(shell, SWT.NONE);
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = margin;
    formLayout.marginHeight = margin;
    wGeneralComp.setLayout(formLayout);
    PropsUi.setLook(wGeneralComp);
    createGeneralTab(wGeneralComp, margin);
    getData();
    return action;
  }

  private void createGeneralTab(Composite wGeneralComp, int margin) {
    wConnection =
        new MetaSelectionLine<>(
            variables,
            metadataProvider,
            KubernetesConnection.class,
            wGeneralComp,
            SWT.NONE,
            BaseMessages.getString(PKG, "ActionKubernetesExec.Dialog.Connection.Label"),
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
      // ignored
    }
    Control last = wConnection;

    last = addLabel(wGeneralComp, "ActionKubernetesExec.Dialog.Pod.Label", null, last, margin);
    wPod = addTextVar(wGeneralComp, last, margin);
    last = wPod;

    last =
        addLabel(
            wGeneralComp,
            "ActionKubernetesExec.Dialog.Container.Label",
            "ActionKubernetesExec.Dialog.Container.Tooltip",
            last,
            margin);
    wContainer = addTextVar(wGeneralComp, last, margin);
    last = wContainer;

    last = addLabel(wGeneralComp, "ActionKubernetesExec.Dialog.Namespace.Label", last, margin);
    wNamespace = addTextVar(wGeneralComp, last, margin);
    last = wNamespace;

    last =
        addLabel(
            wGeneralComp,
            "ActionKubernetesExec.Dialog.Command.Label",
            "ActionKubernetesExec.Dialog.Command.Tooltip",
            last,
            margin);
    wCommand = addTextVar(wGeneralComp, last, margin);
    last = wCommand;

    last =
        addLabel(
            wGeneralComp,
            "ActionKubernetesExec.Dialog.WaitTimeoutSeconds.Label",
            null,
            last,
            margin);
    wWaitTimeoutSeconds = addInt(wGeneralComp, last, margin);
  }

  public void getData() {
    wConnection.setText(Const.NVL(action.getConnectionName(), ""));
    wPod.setText(Const.NVL(action.getPodName(), ""));
    wContainer.setText(Const.NVL(action.getContainerName(), ""));
    wNamespace.setText(Const.NVL(action.getNamespace(), ""));
    wCommand.setText(Const.NVL(action.getCommand(), ""));
    wWaitTimeoutSeconds.setText(
        action.getWaitTimeoutSeconds() > 0 ? Integer.toString(action.getWaitTimeoutSeconds()) : "");
  }

  private void ok() {
    action.setConnectionName(wConnection.getText());
    action.setPodName(wPod.getText());
    action.setContainerName(wContainer.getText());
    action.setNamespace(wNamespace.getText());
    action.setCommand(wCommand.getText());
    String timeout = wWaitTimeoutSeconds.getText();
    action.setWaitTimeoutSeconds(Utils.isEmpty(timeout) ? 0 : Integer.parseInt(timeout.trim()));
    dispose();
  }

  private void cancel() {
    dispose();
  }

  private String lastTooltipKey;

  private Control addLabel(
      Composite parent, String key, String tooltipKey, Control top, int margin) {
    Label label = new Label(parent, SWT.LEFT);
    label.setText(BaseMessages.getString(PKG, key));
    if (tooltipKey != null) {
      label.setToolTipText(BaseMessages.getString(PKG, tooltipKey));
    }
    PropsUi.setLook(label);
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.top = new FormAttachment(top, margin);
    label.setLayoutData(fd);
    return label;
  }

  private Control addLabel(Composite parent, String key, Control top, int margin) {
    lastTooltipKey = tooltipKey(key);
    return addLabel(parent, key, lastTooltipKey, top, margin);
  }

  private TextVar addTextVar(Composite parent, Control top, int margin) {
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

  private String tooltipKey(final String labelKey) {
    return labelKey.endsWith(".Label")
        ? labelKey.substring(0, labelKey.length() - ".Label".length()) + ".Tooltip"
        : labelKey + ".Tooltip";
  }

  private ModifyListener lsMod = e -> changed = true;
}
