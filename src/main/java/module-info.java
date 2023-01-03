module com.example.demobb {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.demobb to javafx.fxml;
    exports com.example.demobb;
    exports com.example.demobb.server;
    opens com.example.demobb.server to javafx.fxml;
}