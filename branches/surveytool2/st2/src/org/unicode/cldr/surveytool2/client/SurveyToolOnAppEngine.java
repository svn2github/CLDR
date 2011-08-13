package org.unicode.cldr.surveytool2.client;

import org.unicode.cldr.surveytool2.shared.FieldVerifier;

import org.unicode.cldr.surveytool2.client.GreetingService;
import org.unicode.cldr.surveytool2.client.GreetingServiceAsync;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class SurveyToolOnAppEngine implements EntryPoint {
  /**
   * The message displayed to the user when the server cannot be reached or
   * returns an error.
   */
  private static final String SERVER_ERROR =
      "An error occurred while " + "attempting to contact the server. Please check your network "
          + "connection and try again.";

  /**
   * Create a remote service proxy to talk to the server-side Greeting service.
   */
  private final GreetingServiceAsync greetingService = GWT.create(GreetingService.class);

  /**
   * This is the entry point method.
   */
  public void onModuleLoad() {
    final Button sendButton = new Button("Send");
    final Button loadButton = new Button("LOAD LOCALE DATA");
    final TextBox nameField = new TextBox();
    final CheckBox resolvedBox = new CheckBox("Resolved");
    final CheckBox equivBox = new CheckBox("Check Equivalence");
    resolvedBox.addClickHandler(new ClickHandler() {
      
      @Override
      public void onClick(ClickEvent event) {
        (greetingService).setResolved(((CheckBox) event.getSource()).getValue(), new AsyncCallback<String>() {

          @Override
          public void onFailure(Throwable caught) {
            System.out.println("Remote Procedure Call - Failure on resolved");
          }

          @Override
          public void onSuccess(String result) {}
        });
        
      }
    });
    equivBox.addClickHandler(new ClickHandler() {
      
      @Override
      public void onClick(ClickEvent event) {
        (greetingService).setEquivTest(((CheckBox) event.getSource()).getValue(), new AsyncCallback<String>() {

          @Override
          public void onFailure(Throwable caught) {
            System.out.println("Remote Procedure Call - Failure on equivalence");
            
          }
          @Override
          public void onSuccess(String result) {}
        });
        
      }
    });
    loadButton.addClickHandler(new ClickHandler() {
      
      @Override
      public void onClick(ClickEvent event) {
        loadButton.setEnabled(false);
        sendButton.setEnabled(false);
        greetingService.loadData(new AsyncCallback<String>() {

          @Override
          public void onFailure(Throwable caught) {
            System.out.println("Data Load FAIL.\n"+caught.getMessage());
            caught.printStackTrace();
            loadButton.setEnabled(true);
            sendButton.setEnabled(true);
          }

          @Override
          public void onSuccess(String result) {
            loadButton.setEnabled(true);
            sendButton.setEnabled(true);
          }
        });
      }
    });
    //final CheckBox 
    //final Button importButton = new Button("Import");
    nameField.setText("Locale Name");
    //regexField.setText("regex");
    final Label errorLabel = new Label();

    // We can add style names to widgets
    sendButton.addStyleName("sendButton");
    //importButton.addStyleName("importButton");

    // Add the nameField and sendButton to the RootPanel
    // Use RootPanel.get() to get the entire body element
    RootPanel.get("nameFieldContainer").add(nameField);
    RootPanel.get("sendButtonContainer").add(sendButton);
    //RootPanel.get("");
    RootPanel.get("testTypeButtons").add(resolvedBox);
    RootPanel.get("testTypeButtons").add(equivBox);
    RootPanel.get("testTypeButtons").add(loadButton);
    RootPanel.get("errorLabelContainer").add(errorLabel);

    // Focus the cursor on the name field when the app loads
    nameField.setFocus(true);
    nameField.selectAll();

    // Create the popup dialog box
    final DialogBox dialogBox = new DialogBox();
    dialogBox.setText("Remote Procedure Call");
    dialogBox.setAnimationEnabled(true);
    final Button closeButton = new Button("Close");
    // We can set the id of a widget by accessing its Element
    closeButton.getElement().setId("closeButton");
    final Label textToServerLabel = new Label();
    final HTML serverResponseLabel = new HTML();
    VerticalPanel dialogVPanel = new VerticalPanel();
    dialogVPanel.addStyleName("dialogVPanel");
    dialogVPanel.add(new HTML("<b>Sending name to the server:</b>"));
    dialogVPanel.add(textToServerLabel);
    dialogVPanel.add(new HTML("<br><b>Server replies:</b>"));
    dialogVPanel.add(serverResponseLabel);
    dialogVPanel.setHorizontalAlignment(VerticalPanel.ALIGN_RIGHT);
    dialogVPanel.add(closeButton);
    dialogBox.setWidget(dialogVPanel);

    // Add a handler to close the DialogBox
    closeButton.addClickHandler(new ClickHandler() {
      public void onClick(ClickEvent event) {
        dialogBox.hide();
        sendButton.setEnabled(true);
        loadButton.setEnabled(true);
        sendButton.setFocus(true);
      }
    });

    // Create a handler for the sendButton and nameField
    class MyHandler implements ClickHandler, KeyUpHandler {
      /**
       * Fired when the user clicks on the sendButton.
       */
      public void onClick(ClickEvent event) {
        sendNameToServer();
      }

      /**
       * Fired when the user types in the nameField.
       */
      public void onKeyUp(KeyUpEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
          sendNameToServer();
        }
      }

      /**
       * Send the name from the nameField to the server and wait for a response.
       */
      private void sendNameToServer() {
        // First, we validate the input.
        errorLabel.setText("");
        String textToServer = nameField.getText();
/*        if (!FieldVerifier.isValidName(textToServer)) {
          errorLabel.setText("Please enter at least four characters");
          return;
        }*/

        // Then, we send the input to the server.
        sendButton.setEnabled(false);
        loadButton.setEnabled(false);
        textToServerLabel.setText(textToServer);
        serverResponseLabel.setText("");
        //((GreetingServiceImpl)greetingService).setRegex(regexField.getText());
        greetingService.greetServer(textToServer, new AsyncCallback<String>() {
          public void onFailure(Throwable caught) {
            // Show the RPC error message to the user
            dialogBox.setText("Remote Procedure Call - Failure");
            serverResponseLabel.addStyleName("serverResponseLabelError");
            serverResponseLabel.setHTML(SERVER_ERROR);
            dialogBox.center();
            closeButton.setFocus(true);
          }

          public void onSuccess(String result) {
            dialogBox.setText("Remote Procedure Call");
            serverResponseLabel.removeStyleName("serverResponseLabelError");
            serverResponseLabel.setHTML(result);
            dialogBox.center();
            closeButton.setFocus(true);
          }
        });
      }
    }

    // Add a handler to send the name to the server
    MyHandler handler = new MyHandler();
    sendButton.addClickHandler(handler);
    nameField.addKeyUpHandler(handler);
  }
}