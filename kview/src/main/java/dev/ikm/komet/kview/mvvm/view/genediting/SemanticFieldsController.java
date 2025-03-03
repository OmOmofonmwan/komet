/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.komet.kview.mvvm.view.genediting;


import static dev.ikm.komet.kview.events.genediting.GenEditingEvent.PUBLISH;
import static dev.ikm.komet.kview.mvvm.viewmodel.FormViewModel.CURRENT_JOURNAL_WINDOW_TOPIC;
import static dev.ikm.komet.kview.mvvm.viewmodel.FormViewModel.VIEW_PROPERTIES;
import static dev.ikm.komet.kview.mvvm.viewmodel.GenEditingViewModel.SEMANTIC;
import static dev.ikm.tinkar.provider.search.Indexer.FIELD_INDEX;
import dev.ikm.komet.framework.events.EvtBusFactory;
import dev.ikm.komet.framework.observable.ObservableField;
import dev.ikm.komet.framework.view.ViewProperties;
import dev.ikm.komet.kview.events.genediting.GenEditingEvent;
import dev.ikm.komet.kview.klfields.KlFieldHelper;
import dev.ikm.tinkar.common.alert.AlertObject;
import dev.ikm.tinkar.common.alert.AlertStreams;
import dev.ikm.tinkar.common.service.TinkExecutor;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.FieldRecord;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.transaction.CommitTransactionTask;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.terms.EntityFacade;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import org.carlfx.cognitive.loader.InjectViewModel;
import org.carlfx.cognitive.viewmodel.ValidationViewModel;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class SemanticFieldsController {

    private static final Logger LOG = LoggerFactory.getLogger(SemanticFieldsController.class);

    @FXML
    private VBox editFieldsVBox;
    @FXML
    private Button cancelButton;

    @FXML
    private Button clearFormButton;

    @FXML
    private Button submitButton;

    @InjectViewModel
    private ValidationViewModel semanticFieldsViewModel;

    private List<ObservableField<?>> observableFields = new ArrayList<>();

    private ChangeListener fieldPropertyChangeListner  = (obs, oldValue, newValue) -> {
        if(newValue instanceof FieldRecord<?> fieldRecord){
           updateStampVersionsNidsForAllFields();
        }
    };
    private EntityFacade semantic;

    private List<Node> nodes = new ArrayList<>();

    @FXML
    private void initialize() {
        // clear all semantic details.
        editFieldsVBox.setSpacing(8.0);
        editFieldsVBox.getChildren().clear();

        EntityFacade semantic = semanticFieldsViewModel.getPropertyValue(SEMANTIC);
        if (semantic != null) {
            StampCalculator stampCalculator = getViewProperties().calculator().stampCalculator();
            Latest<SemanticEntityVersion> semanticEntityVersionLatest = stampCalculator.latest(semantic.nid());
            if (semanticEntityVersionLatest.isPresent()) {
                // Populate the Semantic Details
                // Displaying editable controls and populating the observable fields array list.
                observableFields.addAll(KlFieldHelper
                        .generateObservableFieldsAndNodes(getViewProperties(),
                                nodes,
                                semanticEntityVersionLatest, true));
                editFieldsVBox.getChildren().clear();
            } else {
                // TODO Add a new semantic based on a pattern (blank fields).
            }
        }

        // subscribe to changes... if the FIELD_INDEX is -1 or unset, then the user clicked the
        //  pencil icon and wants to edit all the fields
        // if the FIELD_INDEX is >= 0 then the user chose the context menu of a single field
        //  to edit that field
        semanticFieldsViewModel.getObjectProperty(FIELD_INDEX).subscribe(fieldIndex -> {
            int fieldIdx = (int)fieldIndex;
            editFieldsVBox.getChildren().clear();

            // single field to edit
            if (fieldIdx >= 0) {
                editFieldsVBox.getChildren().add(nodes.get(fieldIdx));
            } else {
                // all fields to edit
                for (int i = 0; i < nodes.size(); i++) {
                    editFieldsVBox.getChildren().add(nodes.get(i));
                    if (i < nodes.size() - 1) {
                        editFieldsVBox.getChildren().add(createSeparator());
                    }
                }
            }
        });
    }

    private void updateStampVersionsNidsForAllFields() {
        EntityFacade semantic = semanticFieldsViewModel.getPropertyValue(SEMANTIC);
        StampCalculator stampCalculator = getViewProperties().calculator().stampCalculator();
        Latest<SemanticEntityVersion> semanticEntityVersionLatest = stampCalculator.latest(semantic.nid());

        semanticEntityVersionLatest.ifPresent(ver -> {
            int latestStampNid = ver.stamp().nid();
            observableFields.forEach(observableField -> {
                //Update the stampNid with the latest stamp nid value.
                observableField.fieldProperty().set(observableField.field().withSemanticVersionStampNid(latestStampNid));
            });
        });
    }

    private static Separator createSeparator() {
        Separator separator = new Separator();
        separator.getStyleClass().add("field-separator");
        return separator;
    }

    public ViewProperties getViewProperties() {
        return semanticFieldsViewModel.getPropertyValue(VIEW_PROPERTIES);
    }

    @FXML
    private void cancel(ActionEvent actionEvent) {
        actionEvent.consume();
        // if previous state was closed cancel will close properties bump out.
        // else show
    }

    @FXML
    private void clearForm(ActionEvent actionEvent) {
        actionEvent.consume();
    }

    @FXML
    public void submit(ActionEvent actionEvent) {
        cancelButton.requestFocus();
        //create new list for passing to the event.
        List<ObservableField<?>> list = new ArrayList<>(observableFields);

        //Get the semantic need to pass along with event for loading values across Opened Semantics.
        EntityFacade semantic = semanticFieldsViewModel.getPropertyValue(SEMANTIC);
        StampCalculator stampCalculator = getViewProperties().calculator().stampCalculator();
        Latest<SemanticEntityVersion> semanticEntityVersionLatest = stampCalculator.latest(semantic.nid());
        semanticEntityVersionLatest.ifPresent(semanticEntityVersion -> {
            StampRecord stamp = Entity.getStamp(semanticEntityVersion.stampNid());
            SemanticVersionRecord version = Entity.getVersionFast(semantic.nid(), stamp.nid());
            Transaction.forVersion(version).ifPresent(transaction -> {
                commitTransactionTask(transaction);
            });
        });

        //EventBus implementation changes to refresh the details area
        EvtBusFactory.getDefaultEvtBus().publish(semanticFieldsViewModel.getPropertyValue(CURRENT_JOURNAL_WINDOW_TOPIC), new GenEditingEvent(actionEvent.getSource(), PUBLISH, list, semantic.nid()));

    }

    private static void commitTransactionTask(Transaction transaction) {
        CommitTransactionTask commitTransactionTask = new CommitTransactionTask(transaction);
        Future<Void> future = TinkExecutor.threadPool().submit(commitTransactionTask);
        TinkExecutor.threadPool().execute(() -> {
            try {
                future.get();
            } catch (Exception e) {
                AlertStreams.getRoot().dispatch(AlertObject.makeError(e));
            }
        });
    }
}
