package com.example.gtupdated;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Comparator;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class HelloApplication extends Application {

    // --- 設定値 ---
    private static final String APP_VERSION = "1.0.3"; // このアプリ自身のバージョン
    private static final String VERSION_URL = "http://YOUR_SERVER/version.json";
    private static boolean DEBUG_MODE = false;
    private static String DEBUG_LOCAL_ZIP_PATH = "upgrade.zip";

    private Stage primaryStage;
    private Label pathLabel;
    private TextField pathInput;
    private TextArea logArea;
    private ProgressBar progressBar;
    private VBox contentPane;
    private Button localTestButton;
    private HBox debugPathBox;
    private TextField debugPathInput;
    private Label infoLabel;
    private HBox pathInputBox;
    private Button updateButton;
    private MenuBar menuBar;
    private Menu debugMenu;
    private TextArea changelogArea;
    private Label statusLabel;


    private final Properties properties = new Properties();
    private final File configFile = new File("config.properties");

    private Path getConfigFilePath() {
        // ユーザーのホームディレクトリを取得 (例: C:\Users\goma)
        String userHome = System.getProperty("user.home");
        // アプリケーション用の設定フォルダのパスを構築 (例: C:\Users\goma\AppData\Roaming\ModpackUpdater)
        // OSごとに適切な場所を自動で選択してくれます
        Path configDir = Paths.get(userHome, "AppData", "Roaming", "ModpackUpdater");
        // 設定ファイルのフルパスを返す
        return configDir.resolve("config.properties");
    }


    @Override
    public void start(Stage primaryStage) {
        // 設定フォルダが存在しない場合は作成する
        try {
            Files.createDirectories(getConfigFilePath().getParent());
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.primaryStage = primaryStage;
        primaryStage.setTitle("Modpack Updater (v" + APP_VERSION + ")");

        // --- ルートレイアウト ---
        BorderPane root = new BorderPane();

        // --- メニューバー ---
        menuBar = new MenuBar();
        Menu viewMenu = new Menu("表示(V)");
        CheckMenuItem debugMenuItem = new CheckMenuItem("デバッグモード");

        debugMenuItem.setOnAction(event -> {
            DEBUG_MODE = debugMenuItem.isSelected();
            logArea.appendText("デバッグモードが " + (DEBUG_MODE ? "有効" : "無効") + " になりました。\n");
            saveProperties(properties.getProperty("game_directory", ""));
            updateDebugUI();
        });
        viewMenu.getItems().add(debugMenuItem);
        menuBar.getMenus().add(viewMenu);
        // --- デバッグメニューの作成 ---
        debugMenu = new Menu("デバッグ(D)");
        MenuItem testPopupItem = new MenuItem("ポップアップをテスト");
        testPopupItem.setOnAction(event -> {
            logArea.appendText("デバッグ: ポップアップテストを実行します。\n");
            showUpdatePopup("v9.9.9 (Test)", "・これはテスト用の変更履歴です。\n・ポップアップが正しく表示されています。", "http://example.com/test.zip");
        });
        MenuItem testProgressBarItem = new MenuItem("プログレスバーをテスト");
        testProgressBarItem.setOnAction(event -> testProgressBar());

        // メニュー項目をデバッグメニューに追加
        debugMenu.getItems().addAll(testPopupItem, testProgressBarItem);
        menuBar.setUseSystemMenuBar(true);
        root.setTop(menuBar);

        // --- UIの各パーツを専用メソッドで作成 ---
        VBox leftPane = createLeftPane();
        VBox rightPane = createRightPane();
        VBox bottomPane = createBottomPane();

        // --- 中央エリアをSplitPaneで分割 ---
        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(leftPane, rightPane);
        splitPane.setDividerPositions(0.6); // 左側が60%の幅になるように初期設定

        root.setCenter(splitPane);
        root.setBottom(bottomPane);

        // --- メインコンテンツ ---
        // ★ 修正点: contentPaneの重複宣言を削除し、メンバー変数を正しく初期化
        contentPane = new VBox(10);
        contentPane.setPadding(new Insets(15));

        // UI要素の作成
        infoLabel = new Label("1. ディレクトリを設定し、2. アップデートを確認してください。");
        pathLabel = new Label("ゲームディレクトリ: (未設定)");
        pathInput = new TextField();
        pathInput.setPromptText("ここにゲームディレクトリのパスを貼り付け");
        logArea = new TextArea();
        logArea.setPromptText("ここに処理ログが表示されます...");
        logArea.setEditable(false);
        Button selectDirButton = new Button("参照...");
        Button setPathButton = new Button("設定");
        pathInputBox = new HBox(5, pathInput, selectDirButton, setPathButton);
        updateButton = new Button("アップデートを確認");
        progressBar = new ProgressBar(0);
        progressBar.prefWidthProperty().bind(contentPane.widthProperty());
        progressBar.setVisible(false);

        // ★ 修正点: デバッグボタンの重複宣言を削除し、メンバー変数を正しく初期化
        localTestButton = new Button("ローカルテスト実行 (DEBUG)");
        localTestButton.setOnAction(event -> {
            logArea.appendText("ローカルテストを開始します...\n");
            logArea.appendText("ローカルの '" + DEBUG_LOCAL_ZIP_PATH + "' を解凍しています...\n");
            try {
                unzip(DEBUG_LOCAL_ZIP_PATH, "temp_upgrade");
                logArea.appendText("解凍が完了しました。\n");
                processManifest("temp_upgrade/manifest.json");
            } catch (IOException e) {
                logArea.appendText("エラー: ローカルテスト中に失敗しました - " + e.getMessage() + "\n");
            }
        });
        Label debugPathLabel = new Label("テスト用Zipパス (デバッグモード):");
        debugPathInput = new TextField();
        Button selectDebugZipButton = new Button("参照...");
        selectDebugZipButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("テスト用のupgrade.zipを選択");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP Archives", "*.zip"));
            File selectedFile = chooser.showOpenDialog(this.primaryStage);
            if (selectedFile != null) {
                // 選択したファイルの絶対パスを入力欄に設定
                debugPathInput.setText(selectedFile.getAbsolutePath());
                // 同時に設定ファイルにも保存する
                saveProperties(properties.getProperty("game_directory", ""));
            }
        });
        // HBoxにデバッグ用のUIをまとめる
        debugPathBox = new HBox(5, debugPathLabel, debugPathInput, selectDebugZipButton);
        // 最初は非表示にしておく
        debugPathBox.setVisible(false);
        debugPathBox.setManaged(false); // 非表示の際にレイアウトスペースを確保しない

        localTestButton = new Button("ローカルテスト実行 (DEBUG)");
        localTestButton.setOnAction(event -> {
            // ★ 修正: メンバー変数のTextFieldからパスを取得する
            String localZipPath = debugPathInput.getText();
            logArea.appendText("ローカルテストを開始します...\n");
            logArea.appendText("ローカルの '" + localZipPath + "' を解凍しています...\n");
            try {
                unzip(localZipPath, "temp_upgrade");
                logArea.appendText("解凍が完了しました。\n");
                processManifest("temp_upgrade/manifest.json");
            } catch (IOException e) {
                logArea.appendText("エラー: ローカルテスト中に失敗しました - " + e.getMessage() + "\n");
            }
        });
        // ボタンのイベント処理
        selectDirButton.setOnAction(event -> handleDirectorySelection());
        setPathButton.setOnAction(event -> handlePathInput());
        updateButton.setOnAction(event -> checkForUpdates());



        // VBoxに「常に表示する」UI要素だけを追加
        contentPane.getChildren().addAll(infoLabel, pathInputBox, pathLabel, updateButton, logArea, progressBar);
        root.setCenter(contentPane);

        // --- 起動時の処理 ---
        loadProperties();
        debugMenuItem.setSelected(DEBUG_MODE);
        updateDebugUI(); // UIの初期状態を正しく設定

        Scene scene = new Scene(root, 800, 600);
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("icon.png")));
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    private VBox createLeftPane() {
        // --- UI要素の作成 ---
        Label gameDirLabel = new Label("ゲームディレクトリを選択");
        pathInput = new TextField(); // Member variable
        pathInput.setPromptText("ここにゲームディレクトリのパスを貼り付け");

        // --- Game Path Section ---
        InputStream iconStream = getClass().getResourceAsStream("/folder-icon.png");
        Objects.requireNonNull(iconStream, "アイコンファイルが見つかりません: folder-icon.png");
        Image folderIcon = new Image(iconStream);
        Button selectDirButton = new Button("", new ImageView(folderIcon)); // Local variable for game path
        selectDirButton.setPrefSize(32, 32);
        Button setPathButton = new Button("設定"); // Local variable for game path
        HBox gamePathBox = new HBox(5, pathInput, selectDirButton, setPathButton); // Use local buttons
        pathLabel = new Label("ゲームディレクトリ: (未設定)"); // Member variable

        // --- Debug Section ---
        Label debugPathLabel = new Label("テスト用Zipパス");
        debugPathInput = new TextField(); // Member variable

        // Re-read the icon stream as it's consumed by the Image constructor
        InputStream iconStream2 = getClass().getResourceAsStream("/folder-icon.png");
        Objects.requireNonNull(iconStream2, "アイコンファイルが見つかりません: folder-icon.png");
        Image folderIcon2 = new Image(iconStream2);
        Button selectDebugZipButton = new Button("", new ImageView(folderIcon2)); // Local variable for debug path
        selectDebugZipButton.setPrefSize(32, 32);
        debugPathBox = new HBox(5, debugPathInput, selectDebugZipButton); // Use local button, assign to member HBox
        localTestButton = new Button("ローカルテスト実行 (DEBUG)"); // Member variable

        // --- Spacer ---
        Pane spacer = new Pane();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // --- イベント処理 ---
        selectDirButton.setOnAction(event -> handleDirectorySelection());
        setPathButton.setOnAction(event -> handlePathInput());
        selectDebugZipButton.setOnAction(event -> { // Use the local debug button variable
            FileChooser chooser = new FileChooser();
            chooser.setTitle("テスト用のupgrade.zipを選択");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP Archives", "*.zip"));
            File selectedFile = chooser.showOpenDialog(primaryStage);
            if (selectedFile != null) {
                debugPathInput.setText(selectedFile.getAbsolutePath());
                saveProperties(properties.getProperty("game_directory", ""));
            }
        });
        localTestButton.setOnAction(event -> { // Member button already has handler set in start() if needed, or set here
            String localZipPath = debugPathInput.getText();
            logArea.appendText("ローカルテストを開始します...\n");
            logArea.appendText("ローカルの '" + localZipPath + "' を解凍しています...\n");
            try {
                unzip(localZipPath, "temp_upgrade");
                logArea.appendText("解凍が完了しました。\n");
                processManifest("temp_upgrade/manifest.json");
            } catch (IOException e) {
                logArea.appendText("エラー: ローカルテスト中に失敗しました - " + e.getMessage() + "\n");
            }
        });

        // --- VBoxに要素を追加 ---
        // Ensure debugPathBox and localTestButton are managed correctly by updateDebugUI initially
        debugPathBox.setVisible(false);
        debugPathBox.setManaged(false);
        localTestButton.setVisible(false);
        localTestButton.setManaged(false);

        VBox leftPane = new VBox(10, gameDirLabel, gamePathBox, pathLabel, debugPathLabel, debugPathBox, localTestButton, spacer);
        leftPane.setPadding(new Insets(15));
        return leftPane;
    }

    private VBox createRightPane() {
        Label versionLabel = new Label("modpack version: (不明)");
        updateButton = new Button("アップデートを確認");
        changelogArea = new TextArea();
        changelogArea.setPromptText("ここに更新履歴が表示されます...");
        changelogArea.setEditable(false);
        VBox.setVgrow(changelogArea, Priority.ALWAYS); // TextAreaが縦に伸びるように設定

        updateButton.setOnAction(event -> checkForUpdates());

        VBox rightPane = new VBox(10, versionLabel, updateButton, changelogArea);
        rightPane.setPadding(new Insets(15));
        return rightPane;
    }
    private VBox createBottomPane() {
        progressBar = new ProgressBar(0);
        statusLabel = new Label("準備完了"); // 新しい1行ログ用のラベル

        progressBar.prefWidthProperty().bind(primaryStage.widthProperty()); // 幅をウィンドウ全体に合わせる
        progressBar.setVisible(false);

        VBox bottomPane = new VBox(5, statusLabel, progressBar);
        bottomPane.setPadding(new Insets(10));
        return bottomPane;
    }

    private void updateDebugUI() {
        boolean isVisible = DEBUG_MODE;

        // デバッグ用UIの表示/非表示を切り替える
        localTestButton.setVisible(isVisible);
        localTestButton.setManaged(isVisible);
        debugPathBox.setVisible(isVisible);
        debugPathBox.setManaged(isVisible);

        // VBox内の要素を一度クリアしてから再構築する
        contentPane.getChildren().clear();
        contentPane.getChildren().addAll(infoLabel, pathInputBox, pathLabel, updateButton);

        if (isVisible) {
            // デバッグモードが有効な場合
            contentPane.getChildren().add(debugPathBox);
            contentPane.getChildren().add(localTestButton);
            // もしメニューバーにデバッグメニューがなければ追加する
            if (!menuBar.getMenus().contains(debugMenu)) {
                menuBar.getMenus().add(debugMenu);
            }
        } else {
            // デバッグモードが無効な場合
            // メニューバーからデバッグメニューを削除する
            menuBar.getMenus().remove(debugMenu);
        }

        // ログエリアとプログレスバーを常に追加
        contentPane.getChildren().addAll(logArea, progressBar);

        // ウィンドウタイトルの更新
        if (isVisible) {
            primaryStage.setTitle("Modpack Updater (v" + APP_VERSION + ") [DEBUG MODE]");
        } else {
            primaryStage.setTitle("Modpack Updater (v" + APP_VERSION + ")");
        }
    }

    private void checkForUpdates() {
        logArea.appendText("アップデートサーバーにバージョン情報を問い合わせています...\n");

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(VERSION_URL)).build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        String jsonBody = response.body();
                        Gson gson = new Gson();
                        JsonObject versionInfo = gson.fromJson(jsonBody, JsonObject.class);

                        String latestVersion = versionInfo.get("latest_version").getAsString();
                        if (latestVersion.compareTo(APP_VERSION) > 0) {
                            String downloadUrl = versionInfo.get("download_url").getAsString();
                            String changelog = versionInfo.get("changelog").getAsString();
                            Platform.runLater(() -> showUpdatePopup(latestVersion, changelog, downloadUrl));
                        } else {
                            Platform.runLater(() -> logArea.appendText("お使いのバージョンは最新です。\n"));
                        }
                    })
                    .exceptionally(e -> {
                        Platform.runLater(() -> logArea.appendText("エラー: アップデート情報の取得に失敗 - " + e.getMessage() + "\n"));
                        return null;
                    });
        }
    }

    private void showUpdatePopup(String newVersion, String changelog, String downloadUrl) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("GT Updated vx.x.x");
        alert.setHeaderText(null); // ヘッダーテキストは不要なのでnullに

        // --- カスタムボタンの作成 ---
        ButtonType updateButtonType = new ButtonType("更新", ButtonBar.ButtonData.OK_DONE);
        ButtonType skipButtonType = new ButtonType("スキップ", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(updateButtonType, skipButtonType);

        // --- カスタムレイアウトの作成 ---
        Label titleLabel = new Label("新しいアップデートがありました");
        Label versionFlowLabel = new Label("ver" + APP_VERSION + " → ver" + newVersion);
        TextArea changelogArea = new TextArea(changelog);
        changelogArea.setEditable(false);
        changelogArea.setWrapText(true);

        VBox content = new VBox(10, titleLabel, versionFlowLabel, changelogArea);
        content.setPadding(new Insets(10));

        // --- アラートにカスタムレイアウトを設定 ---
        alert.getDialogPane().setContent(content);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == updateButtonType) {
            startDownload(downloadUrl);
        } else {
            statusLabel.setText("アップデートがキャンセルされました。");
        }
    }

    private void startDownload(String url) {
        progressBar.setVisible(true);
        logArea.appendText("アップデートファイルのダウンロードを開始します...\nURL: " + url + "\n");

        Task<File> downloadTask = new Task<>() {
            @Override
            protected File call() throws Exception {
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
                // try-with-resourcesでHttpClientを管理
                try (HttpClient client = HttpClient.newHttpClient()) {
                    HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

                    if (response.statusCode() != 200) {
                        throw new IOException("サーバーからの応答が不正です: " + response.statusCode());
                    }

                    long totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                    File downloadedFile = new File("upgrade.zip");
                    long bytesRead = 0;
                    byte[] buffer = new byte[8192];
                    int read;

                    try (InputStream in = response.body(); FileOutputStream out = new FileOutputStream(downloadedFile)) {
                        while ((read = in.read(buffer, 0, buffer.length)) != -1) {
                            out.write(buffer, 0, read);
                            bytesRead += read;
                            updateProgress(bytesRead, totalBytes);
                        }
                    }
                    return downloadedFile;
                }
            }
        };

        progressBar.progressProperty().bind(downloadTask.progressProperty());

        downloadTask.setOnSucceeded(event -> {
            File downloadedFile = downloadTask.getValue();
            logArea.appendText("ダウンロードが完了しました: " + downloadedFile.getName() + "\n");
            progressBar.progressProperty().unbind();
            progressBar.setProgress(1);

            logArea.appendText("アップデートファイルを解凍しています...\n");
            try {
                unzip(downloadedFile.getPath(), "temp_upgrade");
                logArea.appendText("解凍が完了しました。\n");
                processManifest("temp_upgrade/manifest.json");
            } catch (IOException e) {
                logArea.appendText("エラー: ファイルの解凍に失敗しました - " + e.getMessage() + "\n");
            }
        });

        downloadTask.setOnFailed(event -> {
            logArea.appendText("エラー: ダウンロードに失敗しました - " + downloadTask.getException().getMessage() + "\n");
            progressBar.setVisible(false);
            progressBar.progressProperty().unbind();
        });

        new Thread(downloadTask).start();
    }

    private void processManifest(String manifestPath) throws IOException {
        logArea.appendText("アップグレード指示ファイル (manifest.json) を読み込んでいます...\n");

        String gameDirectoryPath = properties.getProperty("game_directory");
        if (gameDirectoryPath == null || gameDirectoryPath.isEmpty()) {
            logArea.appendText("エラー: ゲームディレクトリが設定されていません。ファイル操作を中断します。\n");
            return;
        }

        Path gameDir = Paths.get(gameDirectoryPath);
        Path tempDir = Paths.get("temp_upgrade");

        String content = Files.readString(Paths.get(manifestPath));
        Gson gson = new Gson();
        Manifest manifest = gson.fromJson(content, Manifest.class);

        if (manifest != null && manifest.operations != null) {
            logArea.appendText("ファイル操作を開始します...\n");
            for (UpdateOperation op : manifest.operations) {
                try {
                    if ("delete".equals(op.action)) {
                        Path targetPath = gameDir.resolve(op.target);
                        Files.deleteIfExists(targetPath);
                        logArea.appendText("  - 削除完了: " + op.target + "\n");
                    } else if ("copy".equals(op.action)) {
                        Path sourcePath = tempDir.resolve(op.source);
                        Path destinationPath = gameDir.resolve(op.destination);

                        // コピー先の親ディレクトリが存在しない場合は作成
                        Files.createDirectories(destinationPath.getParent());

                        // ファイルかフォルダかを判断してコピー
                        if (Files.isDirectory(sourcePath)) {
                            copyDirectory(sourcePath, destinationPath);
                        } else {
                            Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                        logArea.appendText("  - コピー完了: " + op.source + " -> " + op.destination + "\n");
                    }
                } catch (IOException e) {
                    logArea.appendText("  - 操作失敗: " + op.action + " - " + e.getMessage() + "\n");
                }
            }
            logArea.appendText("ファイル操作が完了しました。\n");

            // ★★★ 後処理（一時ファイルの削除） ★★★
            cleanup();
            logArea.appendText("アップデートが正常に完了しました！\n");

            // 完了ポップアップを表示
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("成功");
                alert.setHeaderText("アップデートが完了しました。");
                alert.setContentText("アプリケーションを終了してください。");
                alert.showAndWait();
            });

        } else {
            logArea.appendText("警告: manifest.jsonに有効な操作が見つかりませんでした。\n");
        }
    }

    // ★★★ フォルダを再帰的にコピーするためのヘルパーメソッド ★★★
    private void copyDirectory(Path source, Path destination) throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path destinationPath = destination.resolve(source.relativize(sourcePath));
                Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                // Platform.runLaterでUIスレッドにエラーメッセージを渡す
                Platform.runLater(() -> logArea.appendText("  - コピーエラー: " + e.getMessage() + "\n"));
            }
        });
    }

    // ★★★ 一時ファイルを削除するためのヘルパーメソッド ★★★
    private void cleanup() {
        logArea.appendText("一時ファイルをクリーンアップしています...\n");
        try {
            Files.deleteIfExists(Paths.get("upgrade.zip"));
            Path tempUpgradeDir = Paths.get("temp_upgrade");
            if (Files.exists(tempUpgradeDir)) {
                // try-with-resourcesでStreamを管理
                try (Stream<Path> walk = Files.walk(tempUpgradeDir)) {
                    walk.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            }
            logArea.appendText("クリーンアップが完了しました。\n");
        } catch (IOException e) {
            logArea.appendText("エラー: 一時ファイルのクリーンアップに失敗 - " + e.getMessage() + "\n");
        }
    }
    private void unzip(String zipFilePath, String destDirectory) throws IOException {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            if (!destDir.mkdir()) {
                throw new IOException("出力先ディレクトリの作成に失敗しました: " + destDirectory);
            }
        }
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = new File(destDir, zipEntry.getName());
                if (!newFile.getCanonicalPath().startsWith(destDir.getCanonicalPath() + File.separator)) {
                    throw new IOException("Zip Slip 攻撃の可能性があります: " + zipEntry.getName());
                }
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("ディレクトリの作成に失敗しました: " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("ディレクトリの作成に失敗しました: " + parent);
                    }
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
        }
    }

    private void handleDirectorySelection() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Minecraftのゲームディレクトリを選択してください");
        File selectedDirectory = chooser.showDialog(this.primaryStage);
        if (selectedDirectory != null) {
            updatePath(selectedDirectory.getAbsolutePath());
        } else {
            logArea.appendText("フォルダ選択がキャンセルされました。\n");
        }
    }

    private void handlePathInput() {
        String pathFromInput = pathInput.getText();
        if (pathFromInput != null && !pathFromInput.isEmpty()) {
            updatePath(pathFromInput);
        } else {
            logArea.appendText("パスが入力されていません。\n");
        }
    }

    private void updatePath(String newPath) {
        pathInput.setText(newPath);
        pathLabel.setText("ゲームディレクトリ: " + newPath);
        logArea.appendText("ゲームディレクトリが設定されました: " + newPath + "\n");
        saveProperties(newPath);
    }
    private void saveProperties(String gameDirectory) {
        Path configFile = getConfigFilePath();
        try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(configFile.toFile()), StandardCharsets.UTF_8)) {
            properties.setProperty("game_directory", gameDirectory);
            properties.setProperty("debug_mode", String.valueOf(DEBUG_MODE));
            // ★ 修正: TextFieldから値を取得して保存
            properties.setProperty("debug_local_zip_path", debugPathInput.getText());
            properties.store(out, "Modpack Updater Settings");
            logArea.appendText("設定を " + configFile.toString() + " に保存しました。\n");
        } catch (IOException e) {
            logArea.appendText("設定の保存中にエラーが発生しました: " + e.getMessage() + "\n");
        }
    }

    private void loadProperties() {
        Path configFile = getConfigFilePath();
        if (!Files.exists(configFile)) {
            logArea.appendText("設定ファイルが見つかりません。初回起動です。\n");
            return;
        }
        try (InputStreamReader in = new InputStreamReader(new FileInputStream(configFile.toFile()), StandardCharsets.UTF_8)) {
            properties.load(in);

            // ゲームディレクトリを読み込む
            String gameDirectory = properties.getProperty("game_directory");
            if (gameDirectory != null && !gameDirectory.isEmpty()) {
                pathInput.setText(gameDirectory);
                pathLabel.setText("ゲームディレクトリ: " + gameDirectory);
                logArea.appendText("設定を " + configFile.toString() + " から読み込みました。\n");
            }

            // デバッグモード設定を読み込む
            DEBUG_MODE = Boolean.parseBoolean(properties.getProperty("debug_mode", "false"));

            // ★ 修正: 読み込んだ値をTextFieldに設定
            String debugZipPath = properties.getProperty("debug_local_zip_path", "upgrade.zip");
            debugPathInput.setText(debugZipPath);

        } catch (IOException e) {
            logArea.appendText("設定の読み込み中にエラーが発生しました: " + e.getMessage() + "\n");
        }

    }

    public static void main(String[] args) {
        launch(args);
    }
    private void testProgressBar() {
        logArea.appendText("デバッグ: プログレスバーのテストを開始します... (5秒で完了します)\n");
        progressBar.setVisible(true);

        // ダミーのTaskを作成
        Task<Void> dummyTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // 100回のループで進捗をシミュレート
                for (int i = 0; i < 100; i++) {
                    // 50ミリ秒待機 (50ms * 100回 = 5000ms = 5秒)
                    Thread.sleep(50);
                    // UIに進捗を通知 (i+1 が現在の進捗, 100が最大値)
                    updateProgress(i + 1, 100);
                }
                return null;
            }
        };
        // Taskの進捗とProgressBarの進捗を連動させる
        progressBar.progressProperty().bind(dummyTask.progressProperty());

        // Taskが完了した時の処理
        dummyTask.setOnSucceeded(event -> {
            logArea.appendText("デバッグ: プログレスバーのテストが完了しました。\n");
            progressBar.progressProperty().unbind(); // 連動を解除
        });

        // 新しいスレッドでTaskを開始
        new Thread(dummyTask).start();
    }

}