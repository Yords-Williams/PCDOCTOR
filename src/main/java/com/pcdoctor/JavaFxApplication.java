package com.pcdoctor;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JavaFxApplication extends Application {
    private ConfigurableApplicationContext context;

    // Spring crea los servicios y el contexto JavaFX carga después la interfaz visual.
    @Override
    public void init() throws IOException {
        Files.createDirectories(Path.of(System.getProperty("user.home"), "PCDoctor"));
        context = new SpringApplicationBuilder(SpringBootApplication.class).run();
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                JavaFxApplication.class.getResource("/view/dashboard.fxml")
        );
        loader.setControllerFactory(context::getBean);

        Scene scene = new Scene(loader.load(), 1180, 720);
        scene.getStylesheets().add(
                JavaFxApplication.class.getResource("/css/app.css").toExternalForm()
        );

        stage.setTitle("PC Doctor Soporte - Diagnóstico y mantenimiento");
        stage.getIcons().add(
                new Image(JavaFxApplication.class.getResourceAsStream("/icono.png"))
        );
        stage.setMinWidth(1000);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
    }
}
