module com.example.gtupdated {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;

    requires java.net.http;
    requires javafx.graphics;
    requires java.rmi;
    opens com.example.gtupdated to javafx.fxml;
    exports com.example.gtupdated;
}