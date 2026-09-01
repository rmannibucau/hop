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
 */

package org.apache.hop.kubernetes.metadata;

import org.apache.hop.core.Const;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.gui.GuiCompositeWidgetsAdapter;
import org.apache.hop.ui.core.metadata.MetadataEditor;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/** Kubernetes connection metadata editor. */
@SuppressWarnings("java:S2160")
public class KubernetesConnectionEditor extends MetadataEditor<KubernetesConnection> {

  private static final Class<?> PKG = KubernetesConnection.class;

  /** Widgets belonging to the Connection config group. */
  public static final String CONNECTION_WIDGET_ID =
      "KubernetesConnectionEditor.Connection.ParentId";

  private Composite parent;
  private Text wName;
  private GuiCompositeWidgets connectionWidgets;
  private Group gConnection;
  private ScrolledComposite wScrolled;
  private Composite wContent;

  public KubernetesConnectionEditor(
      HopGui hopGui, MetadataManager<KubernetesConnection> manager, KubernetesConnection metadata) {
    super(hopGui, manager, metadata);
  }

  @Override
  public void createControl(final Composite parent) {
    this.parent = parent;

    final PropsUi props = PropsUi.getInstance();
    final int margin = PropsUi.getMargin();
    final int middle = props.getMiddlePct();

    final Label wlName = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlName);
    wlName.setText(BaseMessages.getString(PKG, "KubernetesConnectionEditor.Name.Label"));
    final FormData fdlName = new FormData();
    fdlName.top = new FormAttachment(0, margin * 2);
    fdlName.left = new FormAttachment(0, 0);
    fdlName.right = new FormAttachment(middle, 0);
    wlName.setLayoutData(fdlName);

    wName = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    final FormData fdName = new FormData();
    fdName.top = new FormAttachment(wlName, 0, SWT.CENTER);
    fdName.left = new FormAttachment(middle, margin);
    fdName.right = new FormAttachment(100, 0);
    wName.setLayoutData(fdName);

    final Label spacer = new Label(parent, SWT.HORIZONTAL | SWT.SEPARATOR);
    final FormData fdSpacer = new FormData();
    fdSpacer.left = new FormAttachment(0, 0);
    fdSpacer.top = new FormAttachment(wName, 15);
    fdSpacer.right = new FormAttachment(100, 0);
    spacer.setLayoutData(fdSpacer);

    wScrolled = new ScrolledComposite(parent, SWT.V_SCROLL);
    final FormData fdScrolled = new FormData();
    fdScrolled.left = new FormAttachment(0, 0);
    fdScrolled.right = new FormAttachment(100, 0);
    fdScrolled.top = new FormAttachment(spacer, 15);
    fdScrolled.bottom = new FormAttachment(100, 0);
    wScrolled.setLayoutData(fdScrolled);
    wScrolled.setExpandHorizontal(true);
    wScrolled.setExpandVertical(true);

    wContent = new Composite(wScrolled, SWT.NONE);
    PropsUi.setLook(wContent);
    final FormLayout contentLayout = new FormLayout();
    contentLayout.marginWidth = 0;
    contentLayout.marginHeight = 0;
    wContent.setLayout(contentLayout);
    wScrolled.setContent(wContent);

    gConnection = new Group(wContent, SWT.SHADOW_ETCHED_IN);
    PropsUi.setLook(gConnection);
    gConnection.setText(
        BaseMessages.getString(PKG, "KubernetesConnectionEditor.ConnectionGroup.Label"));
    final FormLayout connectionLayout = new FormLayout();
    connectionLayout.marginWidth = 10;
    connectionLayout.marginHeight = 10;
    gConnection.setLayout(connectionLayout);
    final FormData fdConnection = new FormData();
    fdConnection.left = new FormAttachment(0, 0);
    fdConnection.right = new FormAttachment(100, 0);
    fdConnection.top = new FormAttachment(0, 0);
    gConnection.setLayoutData(fdConnection);

    connectionWidgets = new GuiCompositeWidgets(manager.getVariables());
    connectionWidgets.createCompositeWidgets(
        getMetadata(), null, gConnection, CONNECTION_WIDGET_ID, null);

    wScrolled.addListener(SWT.Resize, e -> relayoutScrolledContent());

    setWidgetsContent();

    wName.addListener(SWT.Modify, e -> setChanged());
    connectionWidgets.setWidgetsListener(
        new GuiCompositeWidgetsAdapter() {
          @Override
          public void widgetModified(
              GuiCompositeWidgets compositeWidgets, Control changedWidget, String widgetId) {
            setChanged();
          }
        });
  }

  @Override
  public void setWidgetsContent() {
    final KubernetesConnection meta = getMetadata();
    wName.setText(Const.NVL(meta.getName(), ""));
    connectionWidgets.setWidgetsContents(meta, gConnection, CONNECTION_WIDGET_ID);
    relayoutScrolledContent();
  }

  @Override
  public void getWidgetsContent(final KubernetesConnection meta) {
    meta.setName(wName.getText());
    connectionWidgets.getWidgetsContents(meta, CONNECTION_WIDGET_ID);
  }

  @Override
  public Button[] createButtonsForButtonBar(final Composite parent) {
    final Button wbTest = new Button(parent, SWT.PUSH | SWT.CENTER);
    PropsUi.setLook(wbTest);
    wbTest.setText(BaseMessages.getString(PKG, "KubernetesConnectionEditor.TestButton.Label"));
    wbTest.addListener(SWT.Selection, e -> test());
    return new Button[] {wbTest};
  }

  public void test() {
    try {
      final KubernetesConnection meta = new KubernetesConnection();
      getWidgetsContent(meta);
      // Building the client validates the kubeconfig, TLS and authentication configuration.
      try (final io.yupiik.fusion.kubernetes.client.KubernetesClient client =
          org.apache.hop.kubernetes.util.KubernetesUtil.createClient(
              meta, manager.getVariables())) {
        // no request: client construction already performs most validation
      }
      final MessageBox box = new MessageBox(parent.getShell(), SWT.ICON_INFORMATION | SWT.OK);
      box.setText(BaseMessages.getString(PKG, "KubernetesConnectionEditor.TestOk.Title"));
      box.setMessage(BaseMessages.getString(PKG, "KubernetesConnectionEditor.TestOk.Message"));
      box.open();
    } catch (final Exception e) {
      new ErrorDialog(
          parent.getShell(),
          BaseMessages.getString(PKG, "KubernetesConnectionEditor.TestError.Title"),
          BaseMessages.getString(PKG, "KubernetesConnectionEditor.TestError.Message"),
          e);
    }
  }

  private void relayoutScrolledContent() {
    if (wScrolled == null || wScrolled.isDisposed() || wContent == null || wContent.isDisposed()) {
      return;
    }
    wContent.layout(true, true);
    final Rectangle client = wScrolled.getClientArea();
    final int width = Math.max(client.width, 1);
    final Point size = wContent.computeSize(width, SWT.DEFAULT);
    wScrolled.setMinWidth(width);
    wScrolled.setMinHeight(size.y);
    wContent.setSize(width, size.y);
  }
}
