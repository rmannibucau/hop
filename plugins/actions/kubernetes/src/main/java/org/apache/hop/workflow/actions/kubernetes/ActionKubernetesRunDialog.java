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

/** Dialog for the Kubernetes Run action. */
public class ActionKubernetesRunDialog extends ActionDialog {

  private static final Class<?> PKG = ActionKubernetesRunDialog.class;

  private ActionKubernetesRun action;
  private MetaSelectionLine<KubernetesConnection> wConnection;
  private TextVar wImage;
  private TextVar wCommand;
  private TextVar wArgs;
  private TextVar wName;
  private TextVar wJobName;
  private Text wCompletions;
  private Text wParallelism;
  private Text wBackoffLimit;
  private Text wTtlSeconds;
  private Text wActiveDeadlineSeconds;
  private TextVar wNamespace;
  private Button wWaitForCompletion;
  private Button wDeleteOnSuccess;
  private Text wTimeoutSeconds;
  private TextVar wAwaitField;
  private TextVar wAwaitValue;
  private boolean changed;

  public ActionKubernetesRunDialog(
      Shell parent, ActionKubernetesRun action, WorkflowMeta workflowMeta, IVariables variables) {
    super(parent, workflowMeta, variables);
    this.action = action;
    if (action.getName() == null) {
      action.setName(BaseMessages.getString(PKG, "ActionKubernetesRun.Name"));
    }
  }

  @Override
  public IAction open() {
    createShell(BaseMessages.getString(PKG, "ActionKubernetesRun.Dialog.Title"), action);
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
            BaseMessages.getString(PKG, "ActionKubernetesRun.Dialog.Connection.Label"),
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

    last =
        addLabel(
            wGeneralComp,
            "ActionKubernetesRun.Dialog.JobName.Label",
            "ActionKubernetesRun.Dialog.JobName.Tooltip",
            last,
            margin);
    wJobName = addTextVar(wGeneralComp, last, margin);
    last = wJobName;

    last = addLabel(wGeneralComp, "ActionKubernetesRun.Dialog.Name.Label", last, margin);
    wName = addTextVar(wGeneralComp, last, margin);
    last = wName;

    last = addLabel(wGeneralComp, "ActionKubernetesRun.Dialog.Image.Label", last, margin);
    wImage = addTextVar(wGeneralComp, last, margin);
    last = wImage;

    last = addLabel(wGeneralComp, "ActionKubernetesRun.Dialog.Command.Label", last, margin);
    wCommand = addTextVar(wGeneralComp, last, margin);
    last = wCommand;

    last = addLabel(wGeneralComp, "ActionKubernetesRun.Dialog.Args.Label", last, margin);
    wArgs = addTextVar(wGeneralComp, last, margin);
    last = wArgs;

    last = addLabel(wGeneralComp, "ActionKubernetesRun.Dialog.Namespace.Label", last, margin);
    wNamespace = addTextVar(wGeneralComp, last, margin);
    last = wNamespace;

    last = addLabel(wGeneralComp, "ActionKubernetesRun.Dialog.Completions.Label", last, margin);
    wCompletions = addInt(wGeneralComp, last, margin);
    last = wCompletions;

    last = addLabel(wGeneralComp, "ActionKubernetesRun.Dialog.Parallelism.Label", last, margin);
    wParallelism = addInt(wGeneralComp, last, margin);
    last = wParallelism;

    last = addLabel(wGeneralComp, "ActionKubernetesRun.Dialog.BackoffLimit.Label", last, margin);
    wBackoffLimit = addInt(wGeneralComp, last, margin);
    last = wBackoffLimit;

    last = addLabel(wGeneralComp, "ActionKubernetesRun.Dialog.TtlSeconds.Label", last, margin);
    wTtlSeconds = addInt(wGeneralComp, last, margin);
    last = wTtlSeconds;

    last =
        addLabel(
            wGeneralComp, "ActionKubernetesRun.Dialog.ActiveDeadlineSeconds.Label", last, margin);
    wActiveDeadlineSeconds = addInt(wGeneralComp, last, margin);
    last = wActiveDeadlineSeconds;

    wWaitForCompletion =
        addCheck(wGeneralComp, "ActionKubernetesRun.Dialog.WaitForCompletion.Label", last, margin);
    last = wWaitForCompletion;

    wDeleteOnSuccess =
        addCheck(wGeneralComp, "ActionKubernetesRun.Dialog.DeleteOnSuccess.Label", last, margin);
    last = wDeleteOnSuccess;

    last = addLabel(wGeneralComp, "ActionKubernetesRun.Dialog.TimeoutSeconds.Label", last, margin);
    wTimeoutSeconds = addInt(wGeneralComp, last, margin);
    last = wTimeoutSeconds;

    last = addLabel(wGeneralComp, "ActionKubernetesRun.Dialog.AwaitField.Label", last, margin);
    wAwaitField = addTextVar(wGeneralComp, last, margin);
    last = wAwaitField;

    last = addLabel(wGeneralComp, "ActionKubernetesRun.Dialog.AwaitValue.Label", last, margin);
    wAwaitValue = addTextVar(wGeneralComp, last, margin);
  }

  public void getData() {
    wConnection.setText(Const.NVL(action.getConnectionName(), ""));
    wJobName.setText(Const.NVL(action.getJobName(), ""));
    wName.setText(Const.NVL(action.getName(), ""));
    wImage.setText(Const.NVL(action.getImage(), ""));
    wCommand.setText(Const.NVL(action.getCommand(), ""));
    wArgs.setText(Const.NVL(action.getArgs(), ""));
    wNamespace.setText(Const.NVL(action.getNamespace(), ""));
    wCompletions.setText(
        action.getCompletions() > 0 ? Integer.toString(action.getCompletions()) : "");
    wParallelism.setText(
        action.getParallelism() > 0 ? Integer.toString(action.getParallelism()) : "");
    wBackoffLimit.setText(
        action.getBackoffLimit() >= 0 ? Integer.toString(action.getBackoffLimit()) : "");
    wTtlSeconds.setText(action.getTtlSeconds() > 0 ? Integer.toString(action.getTtlSeconds()) : "");
    wActiveDeadlineSeconds.setText(
        action.getActiveDeadlineSeconds() > 0
            ? Integer.toString(action.getActiveDeadlineSeconds())
            : "");
    wWaitForCompletion.setSelection(action.isWaitForCompletion());
    wDeleteOnSuccess.setSelection(action.isDeleteOnSuccess());
    wTimeoutSeconds.setText(
        action.getTimeoutSeconds() > 0 ? Integer.toString(action.getTimeoutSeconds()) : "");
    wAwaitField.setText(Const.NVL(action.getAwaitField(), ""));
    wAwaitValue.setText(Const.NVL(action.getAwaitValue(), ""));
  }

  private void ok() {
    action.setConnectionName(wConnection.getText());
    action.setJobName(wJobName.getText());
    action.setName(wName.getText());
    action.setImage(wImage.getText());
    action.setCommand(wCommand.getText());
    action.setArgs(wArgs.getText());
    action.setNamespace(wNamespace.getText());
    action.setCompletions(intValue(wCompletions));
    action.setParallelism(intValue(wParallelism));
    action.setBackoffLimit(intValue(wBackoffLimit));
    action.setTtlSeconds(intValue(wTtlSeconds));
    action.setActiveDeadlineSeconds(intValue(wActiveDeadlineSeconds));
    action.setWaitForCompletion(wWaitForCompletion.getSelection());
    action.setDeleteOnSuccess(wDeleteOnSuccess.getSelection());
    action.setTimeoutSeconds(intValue(wTimeoutSeconds));
    action.setAwaitField(wAwaitField.getText());
    action.setAwaitValue(wAwaitValue.getText());
    dispose();
  }

  private int intValue(Text text) {
    String value = text.getText();
    return Utils.isEmpty(value) ? 0 : Integer.parseInt(value.trim());
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
