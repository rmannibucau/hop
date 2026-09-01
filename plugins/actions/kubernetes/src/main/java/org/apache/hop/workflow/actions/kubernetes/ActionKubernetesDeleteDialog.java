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

/** Dialog for the Kubernetes Delete action. */
public class ActionKubernetesDeleteDialog extends ActionDialog {

  private static final Class<?> PKG = ActionKubernetesDeleteDialog.class;

  private ActionKubernetesDelete action;
  private MetaSelectionLine<KubernetesConnection> wConnection;
  private TextVar wKind;
  private TextVar wName;
  private TextVar wNamespace;
  private TextVar wLabelSelector;
  private Text wGracePeriodSeconds;
  private Button wIgnoreNotFound;
  private boolean changed;

  public ActionKubernetesDeleteDialog(
      Shell parent,
      ActionKubernetesDelete action,
      WorkflowMeta workflowMeta,
      IVariables variables) {
    super(parent, workflowMeta, variables);
    this.action = action;
    if (action.getName() == null) {
      action.setName(BaseMessages.getString(PKG, "ActionKubernetesDelete.Name"));
    }
  }

  @Override
  public IAction open() {
    createShell(BaseMessages.getString(PKG, "ActionKubernetesDelete.Dialog.Title"), action);
    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();
    Composite wGeneralComp = createGeneralComposite();
    createGeneralTab(wGeneralComp);
    getData();
    return action;
  }

  private Composite createGeneralComposite() {
    int margin = PropsUi.getMargin();
    Composite wGeneralComp = new Composite(shell, SWT.NONE);
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = margin;
    formLayout.marginHeight = margin;
    wGeneralComp.setLayout(formLayout);
    PropsUi.setLook(wGeneralComp);
    return wGeneralComp;
  }

  private void createGeneralTab(Composite wGeneralComp) {
    int margin = PropsUi.getMargin();

    wConnection =
        new MetaSelectionLine<>(
            variables,
            metadataProvider,
            KubernetesConnection.class,
            wGeneralComp,
            SWT.NONE,
            BaseMessages.getString(PKG, "ActionKubernetesDelete.Dialog.Connection.Label"),
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

    last = addLabel(wGeneralComp, "ActionKubernetesDelete.Dialog.Kind.Label", last, margin);
    wKind = addTextVar(wGeneralComp, last, margin);
    last = wKind;

    last = addLabel(wGeneralComp, "ActionKubernetesDelete.Dialog.Name.Label", last, margin);
    wName = addTextVar(wGeneralComp, last, margin);
    last = wName;

    last = addLabel(wGeneralComp, "ActionKubernetesDelete.Dialog.Namespace.Label", last, margin);
    wNamespace = addTextVar(wGeneralComp, last, margin);
    last = wNamespace;

    last =
        addLabel(wGeneralComp, "ActionKubernetesDelete.Dialog.LabelSelector.Label", last, margin);
    wLabelSelector = addTextVar(wGeneralComp, last, margin);
    last = wLabelSelector;

    last =
        addLabel(
            wGeneralComp, "ActionKubernetesDelete.Dialog.GracePeriodSeconds.Label", last, margin);
    wGracePeriodSeconds = addInt(wGeneralComp, last, margin);
    last = wGracePeriodSeconds;

    wIgnoreNotFound =
        addCheck(wGeneralComp, "ActionKubernetesDelete.Dialog.IgnoreNotFound.Label", last, margin);
  }

  public void getData() {
    wConnection.setText(Const.NVL(action.getConnectionName(), ""));
    wKind.setText(Const.NVL(action.getKind(), ""));
    wName.setText(Const.NVL(action.getName(), ""));
    wNamespace.setText(Const.NVL(action.getNamespace(), ""));
    wLabelSelector.setText(Const.NVL(action.getLabelSelector(), ""));
    wGracePeriodSeconds.setText(
        action.getGracePeriodSeconds() > 0 ? Integer.toString(action.getGracePeriodSeconds()) : "");
    wIgnoreNotFound.setSelection(action.isIgnoreNotFound());
  }

  private void ok() {
    action.setConnectionName(wConnection.getText());
    action.setKind(wKind.getText());
    action.setName(wName.getText());
    action.setNamespace(wNamespace.getText());
    action.setLabelSelector(wLabelSelector.getText());
    String grace = wGracePeriodSeconds.getText();
    action.setGracePeriodSeconds(Utils.isEmpty(grace) ? 0 : Integer.parseInt(grace.trim()));
    action.setIgnoreNotFound(wIgnoreNotFound.getSelection());
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
