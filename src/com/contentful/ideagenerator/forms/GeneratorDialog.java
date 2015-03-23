/*
 * Copyright (C) 2015 Contentful GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.contentful.ideagenerator.forms;

import com.contentful.generator.Generator;
import com.contentful.java.cma.CMAClient;
import com.contentful.java.cma.model.CMASpace;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.util.ClassUtil;
import com.intellij.ui.DocumentAdapter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import retrofit.RetrofitError;

public class GeneratorDialog extends JDialog {
  private JPanel contentPane;
  private JButton btnGenerate;
  private JButton btnCancel;
  private JTextField tokenTextField;
  private JButton btnLogin;
  private JComboBox comboSpace;
  private JProgressBar progressBar;
  private Project project;
  private List<CMASpace> spaces;

  public GeneratorDialog(Project project) {
    this.project = project;

    setContentPane(contentPane);
    setModal(true);
    getRootPane().setDefaultButton(btnGenerate);
    setResizable(false);

    initComponents();

    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    initWindowListener();
    initContentPane();

    setUIEnabled(false);
    btnCancel.setEnabled(true);
    tokenTextField.setEnabled(true);
  }

  private void initComponents() {
    initGenerate();
    initCancel();
    initToken();
    initLogin();
  }

  private void initGenerate() {
    btnGenerate.addActionListener(new ActionListener() {
      @Override public void actionPerformed(ActionEvent e) {
        PackageChooserDialog dialog = new PackageChooserDialog(
            "Choose destination package", project);

        if (dialog.showAndGet()) {
          PsiPackage selectedPackage = dialog.getSelectedPackage();
          final String sourceRoot = extractSourceRoot(selectedPackage);
          if (sourceRoot == null) {
            return;
          }

          final String qualifiedName = selectedPackage.getQualifiedName();
          final CMASpace space = spaces.get(comboSpace.getSelectedIndex());

          setUIEnabled(false);
          progressBar.setVisible(true);

          ProgressManager.getInstance().run(
              new Task.Backgroundable(project, "Generating Files", false) {
                @Override public void run(final ProgressIndicator progressIndicator) {
                  Exception exception = null;

                  try {
                    new Generator().generate(space.getResourceId(), qualifiedName, sourceRoot,
                        tokenTextField.getText());
                  } catch (Exception e) {
                    exception = e;
                  } finally {
                    final Exception finalException = exception;
                    SwingUtilities.invokeLater(new Runnable() {
                      @Override public void run() {
                        progressBar.setVisible(false);
                        String message;
                        if (finalException == null) {
                          message = "Done! \\o/";
                        } else {
                          message = "Failed: " + finalException.getMessage();
                        }

                        Messages.showDialog(project, message, "Code Generation Completed",
                            new String[]{"OK"}, 0, null);

                        VirtualFileManager.getInstance().syncRefresh();

                        dispose();
                      }
                    });
                  }
                }
              });
        }
      }
    });
  }

  private void initCancel() {
    btnCancel.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onCancel();
      }
    });
  }

  private void initToken() {
    tokenTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override protected void textChanged(DocumentEvent documentEvent) {
        btnLogin.setEnabled(tokenTextField.getText().length() > 0);
      }
    });
  }

  private void initLogin() {
    btnLogin.addActionListener(new ActionListener() {
      @Override public void actionPerformed(ActionEvent e) {
        spaces = null;

        // Reset UI components
        comboSpace.removeAllItems();
        setUIEnabled(false);
        progressBar.setVisible(true);

        ProgressManager.getInstance().run(new Task.Backgroundable(project,
            "Fetching Content Types..", false) {
          @Override public void run(ProgressIndicator progressIndicator) {
            CMAClient client =
                new CMAClient.Builder().setAccessToken(tokenTextField.getText()).build();

            try {
              final List<CMASpace> spaces = client.spaces().fetchAll().getItems();

              SwingUtilities.invokeLater(new Runnable() {
                @Override public void run() {
                  GeneratorDialog.this.spaces = spaces;

                  if (spaces != null && spaces.size() > 0) {
                    for (CMASpace space : spaces) {
                      comboSpace.addItem(space.getName());
                    }

                    setUIEnabled(true);
                  }
                }
              });
            } catch (final RetrofitError e) {
              SwingUtilities.invokeLater(new Runnable() {
                @Override public void run() {
                  showErrorDialog(e);
                }
              });
            } finally {
              SwingUtilities.invokeLater(new Runnable() {
                @Override public void run() {
                  btnLogin.setEnabled(true);
                  btnCancel.setEnabled(true);
                  tokenTextField.setEnabled(true);
                  progressBar.setVisible(false);
                }
              });
            }
          }
        });
      }
    });
  }

  private void showErrorDialog(RetrofitError e) {
    String title = "Error while logging into space";
    String message;

    if (e.getKind() == RetrofitError.Kind.NETWORK) {
      message = "Please check your network connection and try again.";
    } else {
      message = e.getMessage();
      if (message == null) {
        message = "Status code: " + e.getResponse().getStatus();
      }
    }

    Messages.showDialog(title, message, new String[]{"OK"}, 0, null);
  }

  private void initContentPane() {
    contentPane.registerKeyboardAction(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            onCancel();
          }
        },
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  private void initWindowListener() {
    addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        onCancel();
      }
    });
  }

  private void onCancel() {
    dispose();
  }

  private String extractSourceRoot(PsiPackage selectedPackage) {
    PsiDirectory target;
    PsiDirectory[] directories = selectedPackage.getDirectories();

    if (directories.length == 1) {
      target = directories[0];
    } else {
      String[] arr = new String[directories.length];
      for (int i = 0; i < directories.length; i++) {
        arr[i] = directories[i].getVirtualFile().getPath();
      }

      int selectedIndex = ComboChooser.show(
          "Package found in multiple directories",
          "Select package source root:",
          arr);

      if (selectedIndex == -1) {
        return null;
      }

      target = directories[selectedIndex];
    }

    return ClassUtil.sourceRoot(target).getVirtualFile().getPath();
  }

  private void setUIEnabled(boolean enabled) {
    btnGenerate.setEnabled(enabled);
    btnCancel.setEnabled(enabled);
    tokenTextField.setEnabled(enabled);
    btnLogin.setEnabled(enabled);
    comboSpace.setEnabled(enabled);
  }

  public static GeneratorDialog show(Project project) {
    GeneratorDialog dialog = new GeneratorDialog(project);
    dialog.pack();
    dialog.setVisible(true);
    return dialog;
  }
}
