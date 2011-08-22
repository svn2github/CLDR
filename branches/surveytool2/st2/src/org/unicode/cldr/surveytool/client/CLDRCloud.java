package org.unicode.cldr.surveytool.client;

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
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class CLDRCloud implements EntryPoint {
  private String[] rootPanelList = 
  {
   "localeFieldContainer",
   "testTypeButtons",
   "sendButtonContainer",
   "errorLabelContainer",
   "keyFieldContainer",
   "valFieldContainer"
  };
  //keeps the right value for resolved between finds.
  boolean resolvedState=false;
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
  private final CloudTestServiceAsync greetingService = GWT.create(CloudTestService.class);
  
  public void clearPage()
  {
    RootPanel.get().clear();
    for(String s:rootPanelList)
      RootPanel.get(s).clear();
  }

  /**
   * This is the entry point method.
   */
  public void onModuleLoad() {
    final Button sendButton = new Button("Send");
    final Button loadButton = new Button("LOAD LOCALE DATA");
    final ListBox letterList = new ListBox();
    final TextBox localeField = new TextBox();
    final Label localeLabel = new Label("LocaleID or regex");
    final TextBox keyField = new TextBox();
    final Label keyLabel = new Label("Xpath regex");
    final TextBox valField = new TextBox();
    final Label valLabel = new Label("Value regex");
    final CheckBox resolvedBox = new CheckBox("Resolved");
    final RadioButton rb0 = new RadioButton("testGroup", "Find");
    final RadioButton rb1 = new RadioButton("testGroup", "Speed Test");
    final RadioButton rb2 = new RadioButton("testGroup", "Equivalance Test");
    int currentCharValue = 0;
    resolvedBox.setValue(resolvedState);
    clearPage();
    for(int i=0; i<26; ++i)
    {
      //97==a
      letterList.addItem(((char)(i+97))+"");
    }
    ClickHandler loadHandler = new ClickHandler() {
      
      @Override
      public void onClick(ClickEvent event) {
        loadButton.setEnabled(false);
        sendButton.setEnabled(false);
        greetingService.loadData(letterList.getSelectedIndex(), new AsyncCallback<String>() {

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
    };
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
    loadButton.addClickHandler(loadHandler);
    //final CheckBox 
    //final Button importButton = new Button("Import");
    localeField.setText("");
    keyField.setText("");
    valField.setText("");
    rb0.setValue(true);
    //regexField.setText("regex");
    final Label errorLabel = new Label();

    // We can add style names to widgets
    sendButton.addStyleName("sendButton");
    //importButton.addStyleName("importButton");

    // Add the localeField and sendButton to the RootPanel
    // Use RootPanel.get() to get the entire body element
    RootPanel.get("localeFieldContainer").add(localeField);
    RootPanel.get("localeFieldContainer").add(localeLabel);
    RootPanel.get("sendButtonContainer").add(sendButton);
    //RootPanel.get("");
    RootPanel.get("testTypeButtons").add(resolvedBox);
    RootPanel.get("testTypeButtons").add(rb0);
    RootPanel.get("testTypeButtons").add(rb1);
    RootPanel.get("testTypeButtons").add(rb2);
    RootPanel.get("testTypeButtons").add(loadButton);
    RootPanel.get("testTypeButtons").add(letterList);
    RootPanel.get("errorLabelContainer").add(errorLabel);
    RootPanel.get("keyFieldContainer").add(keyField);
    RootPanel.get("keyFieldContainer").add(keyLabel);
    RootPanel.get("valFieldContainer").add(valField);
    RootPanel.get("valFieldContainer").add(valLabel);

    // Focus the cursor on the name field when the app loads
    localeField.setFocus(true);
    localeField.selectAll();

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
        loadButton.setEnabled(true);
        sendButton.setFocus(true);
      }
    });

    // Create a handler for the sendButton and localeField
    class MyHandler implements ClickHandler, KeyUpHandler {
      /**
       * Fired when the user clicks on the sendButton.
       */
      public void onClick(ClickEvent event) {
        sendNameToServer();
      }

      /**
       * Fired when the user types in the localeField.
       */
      public void onKeyUp(KeyUpEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
          sendNameToServer();
        }
      }

      /**
       * Send the name from the localeField to the server and wait for a response.
       */
      private void sendNameToServer() {
        // First, we validate the input.
        errorLabel.setText("");
        String textToServer = localeField.getText();
        String keyRegex = keyField.getText();
        String valRegex = valField.getText();
/*        if (!FieldVerifier.isValidName(textToServer)) {
          errorLabel.setText("Please enter at least four characters");
          return;
        }*/

        // Then, we send the input to the server.
        sendButton.setEnabled(false);
        loadButton.setEnabled(false);
        textToServerLabel.setText(textToServer);
        serverResponseLabel.setText("");
        AsyncCallback<String> testCallBack = new AsyncCallback<String>() {
          //Do nothing in both cases
          @Override
          public void onFailure(Throwable caught) {}
          @Override
          public void onSuccess(String result) {}
          
        };
        int testType = 0;
        if(rb0.getValue())
          testType = 0;
        else if(rb1.getValue())
          testType = 1;
        else
          testType = 2;
        greetingService.setTestType(testType, testCallBack);
        greetingService.greetServer(textToServer, keyRegex, valRegex, new AsyncCallback<String>() {
          public void onFailure(Throwable caught) {
            // Show the RPC error message to the user
            dialogBox.setText("Remote Procedure Call - Failure");
            serverResponseLabel.addStyleName("serverResponseLabelError");
            serverResponseLabel.setHTML(SERVER_ERROR+"\n"+caught.getMessage());
            dialogBox.center();
            closeButton.setFocus(true);
          }

          public void onSuccess(String result) {
            dialogBox.setText("Remote Procedure Call");
            serverResponseLabel.removeStyleName("serverResponseLabelError");
            if(rb0.getValue())
            {
              resolvedState = resolvedBox.getValue();
              loadFindResults(result);
            }
            else
            {    
              serverResponseLabel.setHTML(result);
              dialogBox.center();
              closeButton.setFocus(true);
            }
          }
        });
      }
    }

    // Add a handler to send the name to the server
    MyHandler handler = new MyHandler();
    sendButton.addClickHandler(handler);
    localeField.addKeyUpHandler(handler);
  }
  
  public void loadFindResults(String table)
  {
    Panel tableContainer = new SimplePanel();
    final Button closeResults = new Button("Close find results");
    closeResults.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {onModuleLoad();}});
    clearPage();
    tableContainer.add(new HTML(table));
    RootPanel.get().add(tableContainer);
    RootPanel.get("sendButtonContainer").add(closeResults);
  }
}


