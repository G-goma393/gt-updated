module com.example.gtupdated {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.gtupdated to javafx.fxml;
    exports com.example.gtupdated;
}