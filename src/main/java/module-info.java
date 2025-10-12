module com.example.gtupdated {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;

    requires java.net.http; // ★ この行を追加
    opens com.example.gtupdated to javafx.fxml;
    exports com.example.gtupdated;
}