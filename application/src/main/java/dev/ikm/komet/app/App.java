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
package dev.ikm.komet.app;

import static dev.ikm.komet.amplify.commons.CssHelper.defaultStyleSheet;
import static dev.ikm.komet.amplify.commons.CssHelper.refreshPanes;
import static dev.ikm.komet.app.AppState.*;
import static dev.ikm.komet.framework.KometNodeFactory.KOMET_NODES;
import static dev.ikm.komet.framework.window.WindowSettings.Keys.*;
import static dev.ikm.komet.preferences.JournalWindowSettings.*;

import de.jangassen.MenuToolkit;
import de.jangassen.model.AppearanceMode;
import dev.ikm.komet.amplify.commons.CssHelper;
import dev.ikm.komet.amplify.commons.ResourceHelper;
import dev.ikm.komet.amplify.journal.JournalController;
import dev.ikm.komet.amplify.journal.JournalViewFactory;
import dev.ikm.komet.details.DetailsNodeFactory;
import dev.ikm.komet.framework.KometNode;
import dev.ikm.komet.framework.KometNodeFactory;
import dev.ikm.komet.framework.ScreenInfo;
import dev.ikm.komet.framework.activity.ActivityStreamOption;
import dev.ikm.komet.framework.activity.ActivityStreams;
import dev.ikm.komet.framework.graphics.Icon;
import dev.ikm.komet.framework.graphics.LoadFonts;
import dev.ikm.komet.framework.preferences.KometPreferencesStage;
import dev.ikm.komet.framework.preferences.Reconstructor;
import dev.ikm.komet.framework.tabs.DetachableTab;
import dev.ikm.komet.framework.view.ObservableViewNoOverride;
import dev.ikm.komet.framework.window.KometStageController;
import dev.ikm.komet.framework.window.MainWindowRecord;
import dev.ikm.komet.framework.window.WindowComponent;
import dev.ikm.komet.framework.window.WindowSettings;
import dev.ikm.komet.list.ListNodeFactory;
import dev.ikm.komet.navigator.graph.GraphNavigatorNodeFactory;
import dev.ikm.komet.navigator.pattern.PatternNavigatorFactory;
import dev.ikm.komet.preferences.JournalWindowSettings;
import dev.ikm.komet.preferences.KometPreferences;
import dev.ikm.komet.preferences.KometPreferencesImpl;
import dev.ikm.komet.preferences.Preferences;
import dev.ikm.komet.progress.CompletionNodeFactory;
import dev.ikm.komet.progress.ProgressNodeFactory;
import dev.ikm.komet.reasoner.ReasonerResultsNodeFactory;
import dev.ikm.komet.search.SearchNodeFactory;
import dev.ikm.komet.table.TableNodeFactory;
import dev.ikm.tinkar.common.alert.AlertObject;
import dev.ikm.tinkar.common.alert.AlertStreams;
import dev.ikm.tinkar.common.binary.Encodable;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.TinkExecutor;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.time.Year;
import java.util.*;
import java.util.function.Consumer;
import java.util.prefs.BackingStoreException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX App
 */
public class App extends Application {

    private static final String OS_NAME_MAC = "mac";

    private static final Logger LOG = LoggerFactory.getLogger(App.class);
    public static final String CSS_LOCATION = "dev/ikm/komet/framework/graphics/komet.css";
    public static final SimpleObjectProperty<AppState> state = new SimpleObjectProperty<>(STARTING);

    // preferences folder path for the main komet window's preferences
    public static final String MAIN_KOMET_WINDOW = "main-komet-window";

    // preferences folder path for the journal window(s) preferences
    public static final String JOURNAL_WINDOW = "journal-window";

    public static final String JOURNAL_NAMES = "JOURNAL_NAMES";

    public static final String JOURNAL_FOLDER_PREFIX = "JOURNAL_";

    public static final Double DEFAULT_JOURNAL_HEIGHT = 600.0;

    public static final Double DEFAULT_JOURNAL_WIDTH = 800.0;

    public static final Double DEFAULT_JOURNAL_XPOS = 100.0;

    public static final Double DEFAULT_JOURNAL_YPOS = 50.0;

    private static Stage primaryStage;
    private static Module graphicsModule;
    private static long windowCount = 1;
    private static KometPreferencesStage kometPreferencesStage;

    /**
     * An entry point to launch the newer UI panels.
     */
    private MenuItem createJournalViewMenuItem;

    /**
     * This is a list of new windows that have been launched. During shutdown, the application close each stage gracefully.
     */
    private List<Stage> journalWindows = new ArrayList<>();

    // keep track of journal window numbers as they are created manually or from preferences
    private static int journalWindowNumber = 0;

    public static void main(String[] args) {
        System.setProperty("apple.laf.useScreenMenuBar", "false");
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Komet");
        // https://stackoverflow.com/questions/42598097/using-javafx-application-stop-method-over-shutdownhook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Starting shutdown hook");
            PrimitiveData.save();
            PrimitiveData.stop();
            LOG.info("Finished shutdown hook");
        }));
        launch();
    }

    private static void createNewStage() {
        Stage stage = new Stage();
        stage.setScene(new Scene(new StackPane()));
        stage.setTitle("New stage" + " " + (windowCount++));
        stage.show();
    }

    private static ImmutableList<DetachableTab> makeDefaultLeftTabs(ObservableViewNoOverride windowView) {

        GraphNavigatorNodeFactory navigatorNodeFactory = new GraphNavigatorNodeFactory();
        KometNode navigatorNode1 = navigatorNodeFactory.create(windowView,
                ActivityStreams.NAVIGATION, ActivityStreamOption.PUBLISH.keyForOption(), AlertStreams.ROOT_ALERT_STREAM_KEY);
        DetachableTab navigatorNode1Tab = new DetachableTab(navigatorNode1);


        PatternNavigatorFactory patternNavigatorNodeFactory = new PatternNavigatorFactory();

        KometNode patternNavigatorNode2 = patternNavigatorNodeFactory.create(windowView,
                ActivityStreams.NAVIGATION, ActivityStreamOption.PUBLISH.keyForOption(), AlertStreams.ROOT_ALERT_STREAM_KEY);

        DetachableTab patternNavigatorNode1Tab = new DetachableTab(patternNavigatorNode2);

        return Lists.immutable.of(navigatorNode1Tab, patternNavigatorNode1Tab);
    }

    private static ImmutableList<DetachableTab> makeDefaultCenterTabs(ObservableViewNoOverride windowView) {

        DetailsNodeFactory detailsNodeFactory = new DetailsNodeFactory();
        KometNode detailsNode1 = detailsNodeFactory.create(windowView,
                ActivityStreams.NAVIGATION, ActivityStreamOption.SUBSCRIBE.keyForOption(), AlertStreams.ROOT_ALERT_STREAM_KEY);

        DetachableTab detailsNode1Tab = new DetachableTab(detailsNode1);
        // TODO: setting up tab graphic, title, and tooltip needs to be standardized by the factory...
        detailsNode1Tab.textProperty().bind(detailsNode1.getTitle());
        detailsNode1Tab.tooltipProperty().setValue(detailsNode1.makeToolTip());

        KometNode detailsNode2 = detailsNodeFactory.create(windowView,
                ActivityStreams.SEARCH, ActivityStreamOption.SUBSCRIBE.keyForOption(), AlertStreams.ROOT_ALERT_STREAM_KEY);
        DetachableTab detailsNode2Tab = new DetachableTab(detailsNode2);

        KometNode detailsNode3 = detailsNodeFactory.create(windowView,
                ActivityStreams.UNLINKED, ActivityStreamOption.PUBLISH.keyForOption(), AlertStreams.ROOT_ALERT_STREAM_KEY);
        DetachableTab detailsNode3Tab = new DetachableTab(detailsNode3);

        ListNodeFactory listNodeFactory = new ListNodeFactory();
        KometNode listNode = listNodeFactory.create(windowView,
                ActivityStreams.LIST, ActivityStreamOption.PUBLISH.keyForOption(), AlertStreams.ROOT_ALERT_STREAM_KEY);
        DetachableTab listNodeNodeTab = new DetachableTab(listNode);

        TableNodeFactory tableNodeFactory = new TableNodeFactory();
        KometNode tableNode = tableNodeFactory.create(windowView,
                ActivityStreams.UNLINKED, ActivityStreamOption.PUBLISH.keyForOption(), AlertStreams.ROOT_ALERT_STREAM_KEY);
        DetachableTab tableNodeTab = new DetachableTab(tableNode);

        return Lists.immutable.of(detailsNode1Tab, detailsNode2Tab, detailsNode3Tab, listNodeNodeTab, tableNodeTab);
    }

    private static ImmutableList<DetachableTab> makeDefaultRightTabs(ObservableViewNoOverride windowView) {

        SearchNodeFactory searchNodeFactory = new SearchNodeFactory();
        KometNode searchNode = searchNodeFactory.create(windowView,
                ActivityStreams.SEARCH, ActivityStreamOption.PUBLISH.keyForOption(), AlertStreams.ROOT_ALERT_STREAM_KEY);
        DetachableTab newSearchTab = new DetachableTab(searchNode);

        ProgressNodeFactory progressNodeFactory = new ProgressNodeFactory();
        KometNode kometNode = progressNodeFactory.create(windowView,
                null, null, AlertStreams.ROOT_ALERT_STREAM_KEY);
        DetachableTab progressTab = new DetachableTab(kometNode);

        CompletionNodeFactory completionNodeFactory = new CompletionNodeFactory();
        KometNode completionNode = completionNodeFactory.create(windowView,
                null, null, AlertStreams.ROOT_ALERT_STREAM_KEY);
        DetachableTab completionTab = new DetachableTab(completionNode);

        return Lists.immutable.of(newSearchTab, progressTab, completionTab);
    }

    public void init() throws Exception {
        /*
"/" the local pathname separator
"%t" the system temporary directory
"%h" the value of the "user.home" system property
"%g" the generation number to distinguish rotated logs
"%u" a unique number to resolve conflicts
"%%" translates to a single percent sign "%"
         */
//        String pattern = "%h/Solor/komet/logs/komet%g.log";
//        int fileSizeLimit = 1024 * 1024; //the maximum number of bytes to write to any one file
//        int fileCount = 10;
//        boolean append = true;
//
//        FileHandler fileHandler = new FileHandler(pattern,
//                fileSizeLimit,
//                fileCount,
//                append);

//        File logDirectory = new File(System.getProperty("user.home"), "Solor/komet/logs");
//        logDirectory.mkdirs();
        LOG.info("Starting Komet");
        LoadFonts.load();
        graphicsModule = ModuleLayer.boot()
                .findModule("dev.ikm.komet.framework")
                // Optional<Module> at this point
                .orElseThrow();
    }

    @Override
    public void start(Stage stage) {

        try {
            App.primaryStage = stage;
            Thread.currentThread().setUncaughtExceptionHandler((t, e) -> AlertStreams.getRoot().dispatch(AlertObject.makeError(e)));
            // Get the toolkit
            MenuToolkit tk = MenuToolkit.toolkit();
            Menu kometAppMenu = tk.createDefaultApplicationMenu("Komet");

            MenuItem prefsItem = new MenuItem("Komet preferences...");
            prefsItem.setAccelerator(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.META_DOWN));
            prefsItem.setOnAction(event -> App.kometPreferencesStage.showPreferences());

            kometAppMenu.getItems().add(2, prefsItem);
            kometAppMenu.getItems().add(3, new SeparatorMenuItem());
            MenuItem appleQuit = kometAppMenu.getItems().getLast();
            appleQuit.setOnAction(event -> quit());

            tk.setApplicationMenu(kometAppMenu);
            //tk.setGlobalMenuBar();
            // File Menu
            Menu fileMenu = new Menu("File");
            MenuItem newItem = new MenuItem("New...");
            fileMenu.getItems().addAll(newItem, new SeparatorMenuItem(), tk.createCloseWindowMenuItem(),
                    new SeparatorMenuItem(), new MenuItem("TBD"));

            // Edit
            Menu editMenu = new Menu("Edit");
            editMenu.getItems().addAll(createMenuItem("Undo"), createMenuItem("Redo"), new SeparatorMenuItem(),
                    createMenuItem("Cut"), createMenuItem("Copy"), createMenuItem("Paste"), createMenuItem("Select All"));

            // View
            Menu viewMenu = new Menu("View");
            this.createJournalViewMenuItem = new MenuItem("New _Journal");
            // disable until app state is running
            this.createJournalViewMenuItem.setDisable(true);
            viewMenu.getItems().add(this.createJournalViewMenuItem);
            this.createJournalViewMenuItem.setOnAction(actionEvent -> launchAmplifyDetails("Journal " + (journalWindowNumber + 1), null));

            // Window Menu
            Menu windowMenu = new Menu("Window");
            windowMenu.getItems().addAll(tk.createMinimizeMenuItem(), tk.createZoomMenuItem(), tk.createCycleWindowsItem(),
                    new SeparatorMenuItem(), tk.createBringAllToFrontItem());

            // Help Menu
            Menu helpMenu = new Menu("Help");
            helpMenu.getItems().addAll(new MenuItem("Getting started"));

            MenuBar bar = new MenuBar();
            bar.getMenus().addAll(kometAppMenu, fileMenu, editMenu, viewMenu, windowMenu, helpMenu);
            tk.setAppearanceMode(AppearanceMode.AUTO);
            tk.setDockIconMenu(createDockMenu());
            tk.autoAddWindowMenuItems(windowMenu);


            if(System.getProperty("os.name")!=null && System.getProperty("os.name").toLowerCase().startsWith(OS_NAME_MAC)) {
                tk.setGlobalMenuBar(bar);
            }

            tk.setTrayMenu(createSampleMenu());

            FXMLLoader sourceLoader = new FXMLLoader(getClass().getResource("SelectDataSource.fxml"));
            BorderPane sourceRoot = sourceLoader.load();
            SelectDataSourceController selectDataSourceController = sourceLoader.getController();
            Scene sourceScene = new Scene(sourceRoot, 600, 400);


            sourceScene.getStylesheets()

                    .add(graphicsModule.getClassLoader().getResource(CSS_LOCATION).toString());
            stage.setScene(sourceScene);
            stage.setTitle("KOMET Startup");

            stage.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                ScreenInfo.mouseIsPressed(true);
                ScreenInfo.mouseWasDragged(false);
            });
            stage.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
                ScreenInfo.mouseIsPressed(false);
                ScreenInfo.mouseIsDragging(false);
            });
            stage.addEventFilter(MouseEvent.DRAG_DETECTED, event -> {
                ScreenInfo.mouseIsDragging(true);
                ScreenInfo.mouseWasDragged(true);

            });

            // Ensure app is shutdown gracefully. Once state changes it calls appStateChangeListener.
            stage.setOnCloseRequest(windowEvent -> {
                state.set(SHUTDOWN);
            });
            stage.show();
            state.set(AppState.SELECT_DATA_SOURCE);
            state.addListener(this::appStateChangeListener);

        } catch (IOException e) {
            LOG.error(e.getLocalizedMessage(), e);
            Platform.exit();
        }
    }

    /**
     * When a user selects the menu option View/New Journal a new Stage Window is launched.
     * This method will load a navigation panel to be a publisher and windows will be connected (subscribed) to the activity stream.
     * @param journalName abritrary Journal name.
     * @param journalWindowSettings if present will give the size and positioning of the journal window
     */
    private void launchAmplifyDetails(String journalName, Map<JournalWindowSettings, Object> journalWindowSettings) {
        KometPreferences appPreferences = KometPreferencesImpl.getConfigurationRootPreferences();
        KometPreferences windowPreferences = appPreferences.node(MAIN_KOMET_WINDOW);

        WindowSettings windowSettings = new WindowSettings(windowPreferences);

        Stage amplifyStage = new Stage();
        FXMLLoader amplifyJournalLoader = JournalViewFactory.createFXMLLoader();
        JournalController journalController;
        try {
            BorderPane amplifyJournalBorderPane = amplifyJournalLoader.load();
            journalController = amplifyJournalLoader.getController();
            Scene sourceScene = new Scene(amplifyJournalBorderPane, 1200, 800);

            // Add Komet.css and amplify css
            sourceScene.getStylesheets().addAll(
                    graphicsModule.getClassLoader().getResource(CSS_LOCATION).toString(), CssHelper.defaultStyleSheet());

            // Attach a listener to provide a CSS refresher ability for each Journal window. Right double click settings button (gear)
            attachCSSRefresher(journalController.getSettingsToggleButton(), journalController.getJournalBorderPane());

            amplifyStage.setScene(sourceScene);
            amplifyStage.setTitle(journalName);

            if (journalWindowSettings != null) {
                // load journal specific window settings
                amplifyStage.setMaxHeight((Double)journalWindowSettings.get(JOURNAL_HEIGHT));
                amplifyStage.setMaxWidth((Double)journalWindowSettings.get(JOURNAL_WIDTH));
                amplifyStage.setX((Double)journalWindowSettings.get(JOURNAL_XPOS));
                amplifyStage.setY((Double)journalWindowSettings.get(JOURNAL_YPOS));
            } else {
                amplifyStage.setMaximized(true);
                // if being created manually increment the journal number
                journalWindowNumber++;
            }

            amplifyStage.setOnCloseRequest(windowEvent -> {
                // call shutdown method on the controller
                journalController.shutdown();
                journalWindows.remove(amplifyStage);
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Launch windows window pane inside journal view
        amplifyStage.setOnShown(windowEvent -> {
            //TODO: Refactor factory constructor calls below to use ServiceLoader (make constructors private)
            KometNodeFactory navigatorNodeFactory = new GraphNavigatorNodeFactory();
            KometNodeFactory searchNodeFactory = new SearchNodeFactory();
            KometNodeFactory reasonerNodeFactory = new ReasonerResultsNodeFactory();

            journalController.launchKometFactoryNodes(
                    journalName,
                    windowSettings.getView(),
                    navigatorNodeFactory,
                    searchNodeFactory,
                    reasonerNodeFactory);
        });

        journalWindows.add(amplifyStage);
        amplifyStage.show();
    }

    private void saveJournalWindowsToPreferences() {
        KometPreferences appPreferences = KometPreferencesImpl.getConfigurationRootPreferences();
        KometPreferences journalPreferences = appPreferences.node(JOURNAL_WINDOW);

        List<String> journalSubWindowFolders = new ArrayList<>(journalWindows.size());
        for(Stage journalWindow : journalWindows) {
            String journalSubWindowPrefFolder = JOURNAL_FOLDER_PREFIX + UUID.randomUUID();
            journalSubWindowFolders.add(journalSubWindowPrefFolder);

            KometPreferences journalSubWindowPreferences = appPreferences.node(JOURNAL_WINDOW +
                    File.separator + journalSubWindowPrefFolder);
            journalSubWindowPreferences.put(JOURNAL_TITLE, journalWindow.getTitle());
            journalSubWindowPreferences.putDouble(JOURNAL_HEIGHT, journalWindow.getHeight());
            journalSubWindowPreferences.putDouble(JOURNAL_WIDTH, journalWindow.getWidth());
            journalSubWindowPreferences.putDouble(JOURNAL_XPOS, journalWindow.getX());
            journalSubWindowPreferences.putDouble(JOURNAL_YPOS, journalWindow.getY());
        }
        journalPreferences.putList(JOURNAL_NAMES, journalSubWindowFolders);

        try {
            journalPreferences.flush();
        } catch (BackingStoreException e) {
            LOG.error("error writing journal window flag to preferences", e);
        }
    }

    private void recreateJournalWindows(KometPreferences journalPreferences) {
        KometPreferences appPreferences = KometPreferencesImpl.getConfigurationRootPreferences();
        for (String journalSubWindowPrefFolder : journalPreferences.getList(JOURNAL_NAMES)) {
            KometPreferences journalSubWindowPreferences = appPreferences.node(JOURNAL_WINDOW +
                    File.separator + journalSubWindowPrefFolder);
            Optional<String> journalTitleOptional = journalSubWindowPreferences.get(JOURNAL_TITLE);

            Double height = journalSubWindowPreferences.getDouble(
                    journalSubWindowPreferences.enumToGeneralKey(JOURNAL_HEIGHT), DEFAULT_JOURNAL_HEIGHT);
            Double width = journalSubWindowPreferences.getDouble(
                    journalSubWindowPreferences.enumToGeneralKey(JOURNAL_WIDTH), DEFAULT_JOURNAL_WIDTH);
            Double xpos = journalSubWindowPreferences.getDouble(
                    journalSubWindowPreferences.enumToGeneralKey(JOURNAL_XPOS), DEFAULT_JOURNAL_XPOS);
            Double ypos = journalSubWindowPreferences.getDouble(
                    journalSubWindowPreferences.enumToGeneralKey(JOURNAL_YPOS), DEFAULT_JOURNAL_YPOS);


            Map<JournalWindowSettings, Object> journalWindowSettings = new HashMap<>();
            journalWindowSettings.put(JOURNAL_HEIGHT, height);
            journalWindowSettings.put(JOURNAL_WIDTH, width);
            journalWindowSettings.put(JOURNAL_XPOS, xpos);
            journalWindowSettings.put(JOURNAL_YPOS, ypos);

            if (journalTitleOptional.isPresent()) {
                launchAmplifyDetails(journalTitleOptional.get(), journalWindowSettings);
                // keep track of latest journal number when reloading from preferences
                journalWindowNumber = parseJournalNumber(journalTitleOptional.get());
            }

        }
    }

    private int parseJournalNumber(String journalName) {
        return Integer.parseInt(journalName.split(" ")[1]);
    }

    /**
     * This attaches a listener for the right mouse double click to refresh CSS stylesheet files.
     * @param node The node the user will right mouse button double click
     * @param root The root Parent node to refresh CSS stylesheets.
     */
    private void attachCSSRefresher(Node node, Parent root) {
        // CSS refresher 'easter egg'. Right Click settings button to refresh Css Styling
        node.addEventHandler(MouseEvent.MOUSE_PRESSED, mouseEvent -> {
            if (mouseEvent.getClickCount() == 2 && mouseEvent.isSecondaryButtonDown()) {
                handleRefreshUserCss(root);
            }
        });
    }

    /**
     * Will refresh a parent root node and all children that have CSS stylesheets.
     * Komet.css and amplify-opt2.css files are updated dynamically.
     * @param root Parent node to be traversed to refresh all stylesheets.
     */
    private void handleRefreshUserCss(Parent root) {

        try {
            // "Feature" to make css editing/testing easy in the dev environment. Komet css
            String currentDir = System.getProperty("user.dir").replace("/application", "/framework/src/main/resources");
            String kometCssSourcePath = currentDir + ResourceHelper.toAbsolutePath("komet.css", Icon.class);
            File kometCssSourceFile = new File(kometCssSourcePath);

            // Amplify CSS file
            String amplifyCssPath = defaultStyleSheet().replace("target/classes", "src/main/resources");
            File amplifyCssFile = new File(amplifyCssPath.replace("file:", ""));

            LOG.info("File exists? %s komet css path = %s".formatted(kometCssSourceFile.exists(), kometCssSourceFile));
            LOG.info("File exists? %s amplify css path = %s".formatted(amplifyCssFile.exists(), amplifyCssFile));

            // ensure both exist on the development environment path
            if (kometCssSourceFile.exists() && amplifyCssFile.exists()) {
                Scene scene = root.getScene();

                // Apply Komet css
                scene.getStylesheets().clear();
                scene.getStylesheets().add(kometCssSourceFile.toURI().toURL().toString());

                // Recursively refresh any children using the Amplify css files.
                refreshPanes(root, amplifyCssPath);

                LOG.info("       Updated komet.css: " + kometCssSourceFile.getAbsolutePath());
                LOG.info("Updated amplify css file: " + amplifyCssFile.getAbsolutePath());
            } else {
                LOG.info("File not found for komet.css: " + kometCssSourceFile.getAbsolutePath());
            }
        } catch (IOException e) {
            // TODO: Raise an alert
            e.printStackTrace();
        }

    }

    @Override
    public void stop() {
        LOG.info("Stopping application\n\n###############\n\n");

        // close all journal windows
        journalWindows.forEach(stage -> stage.close());
    }

    private MenuItem createMenuItem(String title) {
        MenuItem menuItem = new MenuItem(title);
        menuItem.setOnAction(this::handleEvent);
        return menuItem;
    }

    private Menu createDockMenu() {
        Menu dockMenu = createSampleMenu();
        MenuItem open = new MenuItem("New Window");
        open.setGraphic(Icon.OPEN.makeIcon());
        open.setOnAction(e -> createNewStage());
        dockMenu.getItems().addAll(new SeparatorMenuItem(), open);
        return dockMenu;
    }

    private Menu createSampleMenu() {
        Menu trayMenu = new Menu();
        trayMenu.setGraphic(Icon.TEMPORARY_FIX.makeIcon());
        MenuItem reload = new MenuItem("Reload");
        reload.setGraphic(Icon.SYNCHRONIZE_WITH_STREAM.makeIcon());
        reload.setOnAction(this::handleEvent);
        MenuItem print = new MenuItem("Print");
        print.setOnAction(this::handleEvent);

        Menu share = new Menu("Share");
        MenuItem mail = new MenuItem("Mail");
        mail.setOnAction(this::handleEvent);
        share.getItems().add(mail);

        trayMenu.getItems().addAll(reload, print, new SeparatorMenuItem(), share);
        return trayMenu;
    }

    private void handleEvent(ActionEvent actionEvent) {
        LOG.debug("clicked " + actionEvent.getSource());  // NOSONAR
    }

    private void appStateChangeListener(ObservableValue<? extends AppState> observable, AppState oldValue, AppState newValue) {
        try {
            switch (newValue) {
                case SELECTED_DATA_SOURCE -> {

                    Platform.runLater(() -> state.set(LOADING_DATA_SOURCE));
                    TinkExecutor.threadPool().submit(new LoadDataSourceTask(state));
                }

                case RUNNING -> {
                    primaryStage.hide();
                    Preferences.start();
                    KometPreferences appPreferences = KometPreferencesImpl.getConfigurationRootPreferences();
                    boolean appInitialized = appPreferences.getBoolean(AppKeys.APP_INITIALIZED, false);
                    if (appInitialized) {
                        LOG.info("Restoring configuration preferences. ");
                    } else {
                        LOG.info("Creating new configuration preferences. ");
                    }

                    MainWindowRecord mainWindowRecord = MainWindowRecord.make();

                    BorderPane kometRoot = mainWindowRecord.root();
                    KometStageController controller = mainWindowRecord.controller();

                    Scene kometScene = new Scene(kometRoot, 1800, 1024);
                    kometScene.getStylesheets()
                            .add(graphicsModule.getClassLoader().getResource(CSS_LOCATION).toString());

                    // if NOT on Mac
                    if(System.getProperty("os.name")!=null && !System.getProperty("os.name").toLowerCase().startsWith(OS_NAME_MAC)) {
                        generateMsWindowsMenu(kometRoot);
                    }


                    primaryStage.setScene(kometScene);

                    KometPreferences windowPreferences = appPreferences.node(MAIN_KOMET_WINDOW);
                    boolean mainWindowInitialized = windowPreferences.getBoolean(KometStageController.WindowKeys.WINDOW_INITIALIZED, false);
                    controller.setup(windowPreferences);
                    primaryStage.setTitle("Komet");
                    //primaryStage.centerOnScreen();

                    if (!mainWindowInitialized) {
                        controller.setLeftTabs(makeDefaultLeftTabs(controller.windowView()), 0);
                        controller.setCenterTabs(makeDefaultCenterTabs(controller.windowView()), 0);
                        controller.setRightTabs(makeDefaultRightTabs(controller.windowView()), 1);
                        windowPreferences.putBoolean(KometStageController.WindowKeys.WINDOW_INITIALIZED, true);
                        appPreferences.putBoolean(AppKeys.APP_INITIALIZED, true);
                    } else {
                        // Restore nodes from preferences.
                        windowPreferences.get(LEFT_TAB_PREFERENCES).ifPresent(leftTabPreferencesName -> {
                            restoreTab(windowPreferences, leftTabPreferencesName, controller.windowView(), node -> controller.leftBorderPaneSetCenter(node));
                        });
                        windowPreferences.get(CENTER_TAB_PREFERENCES).ifPresent(centerTabPreferencesName -> {
                            restoreTab(windowPreferences, centerTabPreferencesName, controller.windowView(), node -> controller.centerBorderPaneSetCenter(node));
                        });
                        windowPreferences.get(RIGHT_TAB_PREFERENCES).ifPresent(rightTabPreferencesName -> {
                            restoreTab(windowPreferences, rightTabPreferencesName, controller.windowView(), node -> controller.rightBorderPaneSetCenter(node));
                        });
                    }
                    primaryStage.setX(controller.windowSettings().xLocationProperty().get());
                    primaryStage.setY(controller.windowSettings().yLocationProperty().get());
                    primaryStage.setHeight(controller.windowSettings().heightProperty().get());
                    primaryStage.setWidth(controller.windowSettings().widthProperty().get());
                    primaryStage.show();
                    //ScenicView.show(kometRoot);

                    App.kometPreferencesStage = new KometPreferencesStage(controller.windowView().makeOverridableViewProperties());

                    windowPreferences.sync();
                    appPreferences.sync();
                    if (createJournalViewMenuItem != null) {
                        createJournalViewMenuItem.setDisable(false);
                        KeyCombination newJournalKeyCombo = new KeyCodeCombination(KeyCode.J, KeyCombination.SHORTCUT_DOWN);
                        createJournalViewMenuItem.setAccelerator(newJournalKeyCombo);
                        KometPreferences journalPreferences = appPreferences.node(JOURNAL_WINDOW);
                        recreateJournalWindows(journalPreferences);
                    }

                }
                case SHUTDOWN -> {
                    // Fork join pool tasks?
                    // Latch of some sort?
                    // Need to put in background thread.
                    quit();
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
            Platform.exit();
        }
    }

    private void generateMsWindowsMenu(BorderPane kometRoot) {
        VBox vBox = (VBox) kometRoot.getTop();
        HBox hBox = (HBox) vBox.getChildren().get(0);

        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");

        MenuItem about = new MenuItem("About");
        about.setOnAction(actionEvent -> showWindowsAboutScreen());
        fileMenu.getItems().add(about);

        MenuItem menuItemQuit = new MenuItem("Quit");
        KeyCombination quitKeyCombo = new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN);
        menuItemQuit.setOnAction(actionEvent -> quit());
        menuItemQuit.setAccelerator(quitKeyCombo);
        fileMenu.getItems().add(menuItemQuit);

        Menu editMenu = new Menu("Edit");

        MenuItem newJournal = new MenuItem("New Journal");
        KeyCombination newJournalKeyCombo = new KeyCodeCombination(KeyCode.J, KeyCombination.CONTROL_DOWN);
        newJournal.setOnAction(actionEvent -> launchAmplifyDetails("Journal " + (journalWindowNumber + 1), null));
        newJournal.setAccelerator(newJournalKeyCombo);
        editMenu.getItems().add(newJournal);

        Menu windowMenu = new Menu("Window");
        MenuItem minimizeWindow = new MenuItem("Minimize");
        KeyCombination minimizeKeyCombo = new KeyCodeCombination(KeyCode.M, KeyCombination.CONTROL_DOWN);
        minimizeWindow.setOnAction(event -> {
          Stage obj = (Stage) kometRoot.getScene().getWindow();
          obj.setIconified(true);
        });
        minimizeWindow.setAccelerator(minimizeKeyCombo);
        windowMenu.getItems().add(minimizeWindow);

        menuBar.getMenus().add(fileMenu);
        menuBar.getMenus().add(editMenu);
        menuBar.getMenus().add(windowMenu);
        hBox.getChildren().add(menuBar);
    }

    private void showWindowsAboutScreen() {
        Stage aboutWindow = new Stage();
        Label kometLabel = new Label("Komet");
        kometLabel.setFont(new Font("Open Sans", 24));
        Label copyright = new Label("Copyright \u00a9 " + Year.now().getValue());
        copyright.setFont(new Font("Open Sans", 10));
        VBox container = new VBox(kometLabel, copyright);
        container.setAlignment(Pos.CENTER);
        Scene aboutScene = new Scene(container, 250, 100);
        aboutWindow.setScene(aboutScene);
        aboutWindow.setTitle("About Komet");
        aboutWindow.show();
    }


    private void quit() {
        //TODO: that this call will likely be moved into the landing page functionality
        saveJournalWindowsToPreferences();
        PrimitiveData.stop();
        Preferences.stop();
        Platform.exit();
    }

    private void restoreTab(KometPreferences windowPreferences, String tabPreferenceNodeName, ObservableViewNoOverride windowView, Consumer<Node> nodeConsumer) {
        LOG.info("Restoring from: " + tabPreferenceNodeName);
        KometPreferences itemPreferences = windowPreferences.node(KOMET_NODES + tabPreferenceNodeName);
        itemPreferences.get(WindowComponent.WindowComponentKeys.FACTORY_CLASS).ifPresent(factoryClassName -> {
            try {
                Class<?> objectClass = Class.forName(factoryClassName);
                Class<? extends Annotation> annotationClass = Reconstructor.class;
                Object[] parameters = new Object[]{windowView, itemPreferences};
                WindowComponent windowComponent = (WindowComponent) Encodable.decode(objectClass, annotationClass, parameters);
                nodeConsumer.accept(windowComponent.getNode());

            } catch (Exception e) {
                AlertStreams.getRoot().dispatch(AlertObject.makeError(e));
            }
        });
    }

    public enum AppKeys {
        APP_INITIALIZED
    }
}
