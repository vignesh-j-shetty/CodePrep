package com.codeprep.app;

import com.codeprep.config.AppConfig;
import java.io.IOException;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import com.codeprep.controller.MainController;

public final class CodePrepApplication extends Application {
    private AppConfig config;

    @Override public void start(Stage stage) throws IOException {
        try {
            config = AppConfig.initializeLibrary();
        } catch (IOException ex) {
            Alert error = new Alert(Alert.AlertType.ERROR, ex.getMessage());
            error.setTitle("Could not open problem library");
            error.setHeaderText("Your library has not been reset");
            error.showAndWait();
            javafx.application.Platform.exit();
            return;
        }
        MainController controller = new MainController(config, stage);
        Scene scene = new Scene((Parent) controller.createView(), 1440, 900);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        stage.setTitle("CodePrep");
        stage.setMinWidth(940);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();
        controller.initialize();
    }

    public static void main(String[] args) { launch(args); }
}
