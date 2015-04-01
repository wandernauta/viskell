package nl.utwente.group10.ui.components;

import javafx.scene.input.MouseEvent;
import nl.utwente.group10.ui.CustomUIPane;
import nl.utwente.group10.ui.gestures.CreateConnectionHandler;

import java.io.IOException;

public class OutputAnchor extends ConnectionAnchor {
    public OutputAnchor(Block block, CustomUIPane pane) throws IOException {
        super(block, pane);
        
        new CreateConnectionHandler(this,pane);
        this.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> pane.setLastOutputAnchor(this));
    }
    
    public Connection createConnectionTo(InputAnchor to){
    	Connection connection = new Connection(this, to);
    	pane.getChildren().add(connection);
    	return connection;
    }
}
