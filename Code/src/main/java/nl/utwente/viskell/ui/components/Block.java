package nl.utwente.viskell.ui.components;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import nl.utwente.viskell.haskell.expr.Apply;
import nl.utwente.viskell.haskell.expr.Expression;
import nl.utwente.viskell.haskell.type.HaskellTypeError;
import nl.utwente.viskell.ui.CircleMenu;
import nl.utwente.viskell.ui.ConnectionCreationManager;
import nl.utwente.viskell.ui.CustomAlert;
import nl.utwente.viskell.ui.CustomUIPane;
import nl.utwente.viskell.ui.serialize.Bundleable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Base block shaped UI Component that other visual elements will extend from.
 * Blocks can add input and output values by implementing the InputBlock and
 * OutputBlock interfaces. Blocks typically are dependent on the ConnectionState
 * and VisualState, although the default invalidateConnectionState() and
 * invalidateVisualState() implementation do nothing.
 * <p>
 * MouseEvents are used for setting the selection state of a block, single
 * clicks can toggle the state to selected. When a block has already been
 * selected and receives another single left click it will toggle a contextual
 * menu for the block.
 * </p>
 * <p>
 * Each block implementation should also feature it's own FXML implementation.
 * </p>
 */
public abstract class Block extends StackPane implements Bundleable, ComponentLoader {
    /** The pane that is used to hold state and place all components on. */
    private CustomUIPane parentPane;
    
    /** The Circle (Context) menu associated with this block instance. */
    private CircleMenu circleMenu;
    
    /** The expression of this Block. */
    protected Expression expr;
    
    /** Property for the ConnectionState. */
    protected IntegerProperty connectionState;
    
    /** Property for the VisualState. */
    protected IntegerProperty visualState;
    
    /** Marker for the expression freshness. */
    private boolean exprIsDirty;

    /**
     * @param pane
     *            The pane this block belongs to.
     */
    public Block(CustomUIPane pane) {
        parentPane = pane;
        int state = ConnectionCreationManager.nextConnectionState();
        connectionState = new SimpleIntegerProperty(state);
        visualState = new SimpleIntegerProperty(state);
        exprIsDirty = true;
        
        // Add listeners to the states.
        // Invalidate listeners give the Block a change to react on the state
        // change before it is cascaded.
        // Cascade listeners make sure state changes are correctly propagated.
        connectionState.addListener(a -> invalidateConnectionState());
        connectionState.addListener(a -> setExprIsDirty());
        connectionState.addListener(this::cascadeConnectionState);
        visualState.addListener(a -> invalidateVisualState());
        visualState.addListener(this::cascadeVisualState);
        
        // Visually react on selection.
        parentPane.selectedBlockProperty().addListener(event -> {
            if (parentPane.getSelectedBlock().isPresent() && this.equals(parentPane.getSelectedBlock().get())) {
                this.getStyleClass().add("selected");
            } else {
                this.getStyleClass().removeAll("selected");
            }
        });
        // Add selection trigger.
        this.addEventHandler(MouseEvent.MOUSE_RELEASED, this::handleMouseEvent);
    }

    /**
     * Sets this block as the selected block.
     * A right click also opens the CircleMenu.
     */
    private void handleMouseEvent(MouseEvent t) {
        parentPane.setSelectedBlock(this);
        if (t.getButton() == MouseButton.SECONDARY) {
            if (this.circleMenu == null) {
                this.circleMenu = new CircleMenu(this);
            }
            
            this.circleMenu.show(t);
        }
    }

    /** @return the parent CustomUIPane of this component. */
    public final CustomUIPane getPane() {
        return parentPane;
    }

    /**
     * @return All InputAnchors of the block.
     */
    public List<InputAnchor> getAllInputs() {
        return ImmutableList.of();
    }

    /**
     * @return Only the active (as specified with by the knot index) inputs.
     */
    public List<InputAnchor> getActiveInputs() {
        return getAllInputs();
    }
    
    /**
     * @return the optional output Anchor for this Block
     * TODO generalize to List<OutputAnchor> getOutputAnchors()
     */
    public Optional<OutputAnchor> getOutputAnchor() {
        return Optional.empty();
    }
    
    /**
     * @return The expression this Block represents.
     * 
     * If the expression is not up-to-date it gets updated.
     */
    public final Expression getExpr() {
        // Assure expr is up-to-date.
        if (this.exprIsDirty) {
            updateExpr();
            this.exprIsDirty = false;
        }
        return expr;
    }
    
    /**
     * Updates the expression
     */
    public abstract void updateExpr();
    

    /** Sets the VisualState. */
    public void updateVisualState(int state) {
        this.visualState.set(state);
    }
    
    /** Sets the ConnectionState to a fresh state. */
    public void updateConnectionState() {
        this.connectionState.set(ConnectionCreationManager.nextConnectionState());
    }

    /** Sets the ConnectionState. */
    public void updateConnectionState(int state) {
        this.connectionState.set(state);
    }
    
    /**
     * Sets the expression to dirty 
     */
    protected void setExprIsDirty() {
        this.exprIsDirty = true;
    }

    /**
     * Called when the ConnectionState changed.
     */
    public void invalidateConnectionState() {
        // Default does nothing.
    }
    
    /**
     * Called when the VisualState changed.
     */
    public void invalidateVisualState() {
        // Default does nothing.
    }
    
    /**
     * ChangeListener that propagates the new ConnectionState to other Blocks
     * that use this Block's output as input.
     * 
     * When the ConnectionState can not be propagated further, a VisualState
     * cascade gets triggered in the reverse direction.
     */
    public void cascadeConnectionState(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
        if (oldValue != newValue) {
            // Boolean to check if this was the last Block that changed.
            boolean cascadedFurther = false;
            
            if (this.getOutputAnchor().isPresent()) {
                for (Optional<? extends ConnectionAnchor> anchor : this.getOutputAnchor().get().getOppositeAnchors()) {
                    if (anchor.isPresent()) {
                        // This Block is an OutputBlock, and that Output is connected to at least 1 Block.
                        anchor.get().updateConnectionState(newValue.intValue());
                        cascadedFurther = true;
                    }
                }
            }
            
            
            if (!cascadedFurther) {
                // The ConnectionState change is not cascaded any further, now a
                // visual update should be propagated upwards.
                try {
                    // Analyze the entire tree.
                    this.getExpr().findType();
                    getPane().setErrorOccurred(false);
                    // TODO: This will set the errorOccurred for the entire
                    // program, not just the invalidated tree. This means that
                    // when having multiple small program trees, errors get
                    // reset to quickly.
                    
                    // No type mismatches.
                } catch (HaskellTypeError e) {
                    // A Type mismatch occurred.
                    int index = -1;
                    // Determine the input index of the Type error.
                    Expression errorExpr = e.getExpression();
                    while (errorExpr instanceof Apply) {
                        errorExpr = ((Apply) errorExpr).getChildren().get(0);
                        index++;
                    }
                    // Get the Block in which the type error occurred and
                    // set the error state for the mismatched input to true.
                    getPane().getExprToFunction(errorExpr).getInput(index).setErrorState(true);
                    // Indicate that an error occurred in the latest analyze attempt.
                    getPane().setErrorOccurred(true);
                }
                
                // Now that the expressions are updated, propagate a visual refresh upwards.
                this.updateVisualState((int) newValue);
                
            }
        }
    }
    
    /**
     * ChangeListener that propagates the new VisualState to other Blocks
     * used as input for this Block.
     */
    public void cascadeVisualState(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
        for (InputAnchor input : this.getActiveInputs()) {
            if (input.isPrimaryConnected()) {
                input.getPrimaryOppositeAnchor().get().getBlock().updateVisualState((int) newValue);
            }
        }
    }
    
    protected void informUnkownHaskellException() {
        String msg = "Whoops! An unkown error has occured. We're sorry, but can't really tell you more than this.";
        CustomAlert alert = new CustomAlert(getPane(), msg);
        getPane().getChildren().add(alert);
        alert.relocate(this.getLayoutX() + 100, this.getLayoutY() + 100);
    }

    /**
     * @return class-specific properties of this Block.
     */
    protected ImmutableMap<String, Object> toBundleFragment() {
        return ImmutableMap.of();
    }

    @Override
    public Map<String, Object> toBundle() {
        return ImmutableMap.of(
            "kind", getClass().getSimpleName(),
            "id", hashCode(),
            "x", getLayoutX(),
            "y", getLayoutY(),
            "properties", toBundleFragment()
        );
    }
}
