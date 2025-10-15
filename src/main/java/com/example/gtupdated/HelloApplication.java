package com.example.gtupdated;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.BorderPane;
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

        // ★★★ ルートレイアウトをVBoxからBorderPaneに変更 ★★★
        BorderPane root = new BorderPane();

        // --- メニューバーの作成 ---
        MenuBar menuBar = new MenuBar();
        Menu viewMenu = new Menu("表示(V)");
        CheckMenuItem debugMenuItem = new CheckMenuItem("デバッグモード");

        debugMenuItem.setOnAction(event -> {
            DEBUG_MODE = debugMenuItem.isSelected();
            // ここにデバッグ用UIの表示/非表示を切り替える処理を後で追加
            logArea.appendText("デバッグモードが " + (DEBUG_MODE ? "有効" : "無効") + " になりました。\n");
        });
        viewMenu.getItems().add(debugMenuItem);
        menuBar.getMenus().add(viewMenu);

        // ★ Macの場合、OSのメニューバーに統合する設定 (Windows/Linuxでは何もしない)
        menuBar.setUseSystemMenuBar(true);

        // ★★★ BorderPaneの上部にメニューバーを配置 ★★★
        root.setTop(menuBar);

        // --- メインコンテンツの作成 (左ペイン) ---
        VBox leftPane = new VBox(10);
        leftPane.setPadding(new Insets(15));

        Label infoLabel = new Label("1. ディレクトリを設定し、2. アップデートを確認してください。");
        pathLabel = new Label("ゲームディレクトリ: (未設定)");
        pathInput = new TextField();
        pathInput.setPromptText("ここにゲームディレクトリのパスを貼り付け");
        logArea = new TextArea();
        logArea.setPromptText("ここに処理ログが表示されます...");
        logArea.setEditable(false);
        Button selectDirButton = new Button("参照...");
        Button setPathButton = new Button("設定");
        HBox pathInputBox = new HBox(5, pathInput, selectDirButton, setPathButton);
        Button updateButton = new Button("アップデートを確認");
        progressBar = new ProgressBar(0);
        progressBar.prefWidthProperty().bind(leftPane.widthProperty()); // 幅を親のVBoxに合わせる
        progressBar.setVisible(false);

        selectDirButton.setOnAction(event -> handleDirectorySelection());
        setPathButton.setOnAction(event -> handlePathInput());
        updateButton.setOnAction(event -> checkForUpdates());

        leftPane.getChildren().addAll(infoLabel, pathInputBox, pathLabel, updateButton, logArea, progressBar);

        // ★★★ BorderPaneの中央にメインコンテンツを配置 ★★★
        root.setCenter(leftPane);

        // --- 起動時の処理 ---
        loadProperties();
        debugMenuItem.setSelected(DEBUG_MODE);
        // デバッグモードが有効なら、テストボタンを追加
        if (DEBUG_MODE) {
            Button localTestButton = new Button("ローカルテスト実行 (DEBUG)");
            localTestButton.setOnAction(event -> {
                // ... (ローカルテストの処理)
            });
            // updateButton(インデックス3)とlogArea(インデックス4)の間にボタンを挿入
            leftPane.getChildren().add(4, localTestButton);
        }

        Scene scene = new Scene(root, 500, 450); // メニューバー分、少し高さを広げます
        primaryStage.setScene(scene);
        primaryStage.show();
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
        alert.setTitle("アップデートが見つかりました");
        alert.setHeaderText("新しいバージョン " + newVersion + " が利用可能です。");
        alert.setContentText("更新内容:\n" + changelog);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            startDownload(downloadUrl);
        } else {
            logArea.appendText("アップデートがキャンセルされました。\n");
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
            properties.setProperty("debug_local_zip_path", DEBUG_LOCAL_ZIP_PATH);
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

            String gameDirectory = properties.getProperty("game_directory");
            if (gameDirectory != null && !gameDirectory.isEmpty()) {
                pathInput.setText(gameDirectory);
                pathLabel.setText("ゲームディレクトリ: " + gameDirectory);
                logArea.appendText("設定を " + configFile.toString() + " から読み込みました。\n");
            }

            DEBUG_MODE = Boolean.parseBoolean(properties.getProperty("debug_mode", "false"));
            if (DEBUG_MODE) {
                DEBUG_LOCAL_ZIP_PATH = properties.getProperty("debug_local_zip_path", "upgrade.zip");
                logArea.appendText("★★ デバッグモードが有効です ★★\n");
                if (primaryStage != null) { // primaryStageが初期化済みか確認
                    primaryStage.setTitle(primaryStage.getTitle() + " [DEBUG MODE]");
                }
            }
        } catch (IOException e) {
            logArea.appendText("設定の読み込み中にエラーが発生しました: " + e.getMessage() + "\n");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}